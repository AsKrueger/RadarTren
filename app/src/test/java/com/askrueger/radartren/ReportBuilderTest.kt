package com.askrueger.radartren

import com.askrueger.radartren.domain.Core
import com.askrueger.radartren.domain.IncidentCategory
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.MetricSample
import com.askrueger.radartren.domain.PeriodType
import com.askrueger.radartren.domain.ReportBuilder
import com.askrueger.radartren.domain.ReportIncident
import com.askrueger.radartren.domain.ReportPeriod
import com.askrueger.radartren.domain.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReportBuilderTest {
    private val day = ReportPeriod(PeriodType.DAY, LocalDate.of(2026, 10, 31))

    private fun sample(at: Long) = MetricSample(at, 100, 100, 20, 5, 300, 45, 2)

    private fun incident(severity: Severity, verified: Boolean = true) = ReportIncident(
        severity, IncidentCategory.DELAY, IncidentSource.DETECTED, Core.MADRID, "x", day.startEpoch, day.startEpoch, verified,
    )

    @Test
    fun `periods cover the calendar and name their files`() {
        assertEquals("dia-2026-10-31", day.id)
        assertEquals("semana-2026-W44", ReportPeriod(PeriodType.WEEK, LocalDate.of(2026, 10, 26)).id)
        assertEquals("mes-2026-10", ReportPeriod(PeriodType.MONTH, LocalDate.of(2026, 10, 1)).id)
        assertEquals("anio-2025", ReportPeriod(PeriodType.YEAR, LocalDate.of(2025, 1, 1)).id)
    }

    @Test
    fun `latest closed periods exclude the current ones`() {
        val closed = ReportPeriod.latestClosed(LocalDate.of(2026, 11, 1)) // A Sunday.
        assertEquals(LocalDate.of(2026, 10, 31), closed[0].start)
        assertEquals(LocalDate.of(2026, 10, 19), closed[1].start)
        assertEquals(LocalDate.of(2026, 10, 1), closed[2].start)
        assertEquals(LocalDate.of(2025, 1, 1), closed[3].start)
    }

    @Test
    fun `the day of the clock change keeps its real length`() {
        // 25 October 2026 lasts 25 hours in Spain.
        val change = ReportPeriod(PeriodType.DAY, LocalDate.of(2026, 10, 25))
        assertEquals(25 * 3600L, change.endEpoch - change.startEpoch)
    }

    @Test
    fun `figures and coverage come from the samples`() {
        val samples = List(48) { sample(day.startEpoch + it * 900L) }
        val data = ReportBuilder.build(day, samples, listOf(incident(Severity.HIGH)), 0)
        assertEquals(0.5, data.coverage, 0.001)
        assertEquals(0.8, data.punctuality!!, 0.001)
        assertEquals(3.0, data.averageDelayMinutes!!, 0.001)
        assertEquals(45, data.maxDelayMinutes)
        assertEquals(1, data.totalIncidents)
    }

    @Test
    fun `unverified alerts never reach the totals`() {
        val data = ReportBuilder.build(
            day, emptyList(), listOf(incident(Severity.HIGH), incident(Severity.HIGH, verified = false)), 0,
        )
        assertEquals(1, data.totalIncidents)
        assertEquals(1, data.unverifiedAlerts)
        assertEquals(1, data.topIncidents.size)
    }

    @Test
    fun `no samples means no punctuality instead of a perfect score`() {
        val data = ReportBuilder.build(day, emptyList(), emptyList(), 0)
        assertNull(data.punctuality)
        assertEquals(0.0, data.coverage, 0.0)
    }

    @Test
    fun `top incidents are ordered by severity`() {
        val data = ReportBuilder.build(day, emptyList(), listOf(incident(Severity.LOW), incident(Severity.CRITICAL)), 0)
        assertEquals(Severity.CRITICAL, data.topIncidents.first().severity)
    }
}
