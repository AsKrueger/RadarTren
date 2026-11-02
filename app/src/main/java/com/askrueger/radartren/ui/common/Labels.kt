package com.askrueger.radartren.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.graphics.Color
import com.askrueger.radartren.R
import com.askrueger.radartren.data.AppLanguage
import com.askrueger.radartren.domain.DelayLevel
import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.IncidentCategory
import com.askrueger.radartren.domain.PeriodType
import com.askrueger.radartren.domain.ReportPeriod
import com.askrueger.radartren.domain.Severity
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Workers and notifications run outside any activity, so they apply the chosen language here. */
fun localized(context: Context, language: AppLanguage): Context {
    val tag = language.tag ?: return context
    val configuration = Configuration(context.resources.configuration)
    configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
    return context.createConfigurationContext(configuration)
}

fun label(context: Context, severity: Severity): String = context.getString(
    when (severity) {
        Severity.CRITICAL -> R.string.severity_critical
        Severity.HIGH -> R.string.severity_high
        Severity.MEDIUM -> R.string.severity_medium
        Severity.LOW -> R.string.severity_low
        Severity.INFO -> R.string.severity_info
    },
)

fun categoryLabel(context: Context, category: IncidentCategory): String = context.getString(
    when (category) {
        IncidentCategory.SAFETY -> R.string.category_safety
        IncidentCategory.SERVICE_INTERRUPTED -> R.string.category_interrupted
        IncidentCategory.DISRUPTION -> R.string.category_disruption
        IncidentCategory.DELAY -> R.string.category_delay
        IncidentCategory.CANCELLATION -> R.string.category_cancellation
        IncidentCategory.PLANNED_WORKS -> R.string.category_works
        IncidentCategory.ACCESSIBILITY -> R.string.category_accessibility
        IncidentCategory.INFORMATION -> R.string.category_information
    },
)

fun feedLabel(context: Context, feed: Feed): String = context.getString(
    when (feed) {
        Feed.COMMUTER_POSITIONS -> R.string.feed_commuter_positions
        Feed.LONG_DISTANCE_POSITIONS -> R.string.feed_long_positions
        Feed.COMMUTER_UPDATES -> R.string.feed_commuter_updates
        Feed.LONG_DISTANCE_UPDATES -> R.string.feed_long_updates
        Feed.ALERTS -> R.string.feed_alerts
    },
)

fun periodTitle(period: ReportPeriod, locale: Locale): String = when (period.type) {
    PeriodType.DAY -> period.start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    PeriodType.WEEK -> {
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        val week = period.start.get(WeekFields.ISO.weekOfWeekBasedYear())
        "W$week · ${period.start.format(format)} – ${period.end.minusDays(1).format(format)}"
    }
    PeriodType.MONTH -> period.start.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { it.titlecase(locale) }
    PeriodType.YEAR -> period.start.year.toString()
}

/** A time of day in the phone's zone, for "since 10:41" style labels. */
fun clock(epochSeconds: Long, seconds: Boolean = false): String =
    java.time.Instant.ofEpochSecond(epochSeconds).atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (seconds) "HH:mm:ss" else "HH:mm"))

/** The same colors as [severityColor], for layers drawn by the map engine. */
fun severityColorHex(severity: Severity): String = when (severity) {
    Severity.CRITICAL -> "#B00020"
    Severity.HIGH -> "#D9480F"
    Severity.MEDIUM -> "#C98A00"
    Severity.LOW -> "#2E7D50"
    Severity.INFO -> "#5A6676"
}

fun severityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICAL -> Color(0xFFB00020)
    Severity.HIGH -> Color(0xFFD9480F)
    Severity.MEDIUM -> Color(0xFFC98A00)
    Severity.LOW -> Color(0xFF2E7D50)
    Severity.INFO -> Color(0xFF5A6676)
}

/** Colors of the trains on the map, shared with the legend. */
fun delayColorHex(level: DelayLevel): String = when (level) {
    DelayLevel.UNKNOWN -> "#7A8797"
    DelayLevel.ON_TIME -> "#2E9E5B"
    DelayLevel.MINOR -> "#E0A800"
    DelayLevel.MODERATE -> "#E8590C"
    DelayLevel.SEVERE -> "#C2183A"
}
