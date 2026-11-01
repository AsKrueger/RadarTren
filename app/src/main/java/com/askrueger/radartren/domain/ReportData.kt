package com.askrueger.radartren.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

val SPAIN_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

/** The background job targets one sample every 15 minutes, which defines 100 % coverage. */
const val SAMPLE_INTERVAL_SECONDS = 15 * 60L

enum class PeriodType { DAY, WEEK, MONTH, YEAR }

/** A closed calendar period in Spanish time. [end] is exclusive. */
data class ReportPeriod(val type: PeriodType, val start: LocalDate) {
    val end: LocalDate
        get() = when (type) {
            PeriodType.DAY -> start.plusDays(1)
            PeriodType.WEEK -> start.plusWeeks(1)
            PeriodType.MONTH -> start.plusMonths(1)
            PeriodType.YEAR -> start.plusYears(1)
        }

    val startEpoch: Long get() = start.atStartOfDay(SPAIN_ZONE).toEpochSecond()
    val endEpoch: Long get() = end.atStartOfDay(SPAIN_ZONE).toEpochSecond()

    /** Stable id, also used as the PDF file name. */
    val id: String
        get() = when (type) {
            PeriodType.DAY -> "dia-$start"
            PeriodType.WEEK -> {
                val week = start.get(WeekFields.ISO.weekOfWeekBasedYear())
                "semana-${start.get(WeekFields.ISO.weekBasedYear())}-W%02d".format(week)
            }
            PeriodType.MONTH -> "mes-%d-%02d".format(start.year, start.monthValue)
            PeriodType.YEAR -> "anio-${start.year}"
        }

    companion object {
        /** The most recent fully closed period of each type before [today]. */
        fun latestClosed(today: LocalDate): List<ReportPeriod> {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return listOf(
                ReportPeriod(PeriodType.DAY, today.minusDays(1)),
                ReportPeriod(PeriodType.WEEK, monday.minusWeeks(1)),
                ReportPeriod(PeriodType.MONTH, today.withDayOfMonth(1).minusMonths(1)),
                ReportPeriod(PeriodType.YEAR, today.withDayOfYear(1).minusYears(1)),
            )
        }
    }
}

/** One background collection, condensed. Rows of this kind are the raw material of the reports. */
data class MetricSample(
    val at: Long,
    val trains: Int,
    val withDelayInfo: Int,
    val delayed5: Int,
    val delayed15: Int,
    val delaySumMinutes: Int,
    val maxDelayMinutes: Int,
    val cancelled: Int,
)

data class ReportIncident(
    val severity: Severity,
    val category: IncidentCategory,
    val source: IncidentSource,
    val core: Core?,
    val text: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val verified: Boolean,
)

data class DailyPoint(val date: LocalDate, val incidents: Int, val punctuality: Double?)

data class ReportData(
    val period: ReportPeriod,
    val generatedAt: Long,
    val samples: Int,
    /** Share of the period actually observed, from 0 to 1. A figure with low coverage is partial. */
    val coverage: Double,
    /** Share of observed trains running less than 5 minutes late. */
    val punctuality: Double?,
    val averageDelayMinutes: Double?,
    val maxDelayMinutes: Int,
    val peakCancelled: Int,
    val bySeverity: Map<Severity, Int>,
    val byCore: List<Pair<Core?, Int>>,
    val daily: List<DailyPoint>,
    val topIncidents: List<ReportIncident>,
    val unverifiedAlerts: Int,
) {
    val totalIncidents: Int get() = bySeverity.values.sum()
}

object ReportBuilder {
    private const val TOP_INCIDENTS = 15

    /**
     * Unverified notices (published while their feed was frozen) are counted apart and kept out of
     * every total, so a stuck feed cannot inflate a report.
     */
    fun build(
        period: ReportPeriod,
        samples: List<MetricSample>,
        incidents: List<ReportIncident>,
        generatedAt: Long,
    ): ReportData {
        val verified = incidents.filter { it.verified }
        val withDelay = samples.sumOf { it.withDelayInfo }
        val expected = (period.endEpoch - period.startEpoch) / SAMPLE_INTERVAL_SECONDS

        return ReportData(
            period = period,
            generatedAt = generatedAt,
            samples = samples.size,
            coverage = (samples.size.toDouble() / expected).coerceAtMost(1.0),
            punctuality = withDelay.takeIf { it > 0 }?.let { 1 - samples.sumOf { s -> s.delayed5 }.toDouble() / it },
            averageDelayMinutes = withDelay.takeIf { it > 0 }?.let { samples.sumOf { s -> s.delaySumMinutes }.toDouble() / it },
            maxDelayMinutes = samples.maxOfOrNull { it.maxDelayMinutes } ?: 0,
            peakCancelled = samples.maxOfOrNull { it.cancelled } ?: 0,
            bySeverity = Severity.entries.associateWith { s -> verified.count { it.severity == s } },
            byCore = verified.groupingBy { it.core }.eachCount().entries
                .sortedByDescending { it.value }.map { it.key to it.value },
            daily = daily(period, samples, verified),
            topIncidents = verified
                .sortedWith(compareByDescending<ReportIncident> { it.severity }.thenBy { it.firstSeen })
                .take(TOP_INCIDENTS),
            unverifiedAlerts = incidents.size - verified.size,
        )
    }

    private fun daily(period: ReportPeriod, samples: List<MetricSample>, incidents: List<ReportIncident>): List<DailyPoint> {
        if (period.type == PeriodType.DAY) return emptyList()
        return generateSequence(period.start) { it.plusDays(1) }.takeWhile { it < period.end }.map { date ->
            val from = date.atStartOfDay(SPAIN_ZONE).toEpochSecond()
            val to = date.plusDays(1).atStartOfDay(SPAIN_ZONE).toEpochSecond()
            val day = samples.filter { it.at in from until to }
            val known = day.sumOf { it.withDelayInfo }
            DailyPoint(
                date = date,
                incidents = incidents.count { it.firstSeen in from until to },
                punctuality = known.takeIf { it > 0 }?.let { 1 - day.sumOf { s -> s.delayed5 }.toDouble() / it },
            )
        }.toList()
    }
}
