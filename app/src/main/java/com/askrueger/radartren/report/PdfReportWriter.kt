package com.askrueger.radartren.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.askrueger.radartren.R
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.PeriodType
import com.askrueger.radartren.domain.ReportData
import com.askrueger.radartren.domain.SPAIN_ZONE
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.ui.common.label
import com.askrueger.radartren.ui.common.periodTitle
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val PAGE_WIDTH = 595 // A4 in points.
private const val PAGE_HEIGHT = 842
private const val MARGIN = 44f
private const val LOW_COVERAGE = 0.8

/** Draws a [ReportData] as an A4 PDF, in the language of [context]. */
class PdfReportWriter(private val context: Context) {
    private val ink = Color.rgb(24, 32, 44)
    private val muted = Color.rgb(90, 102, 118)
    private val accent = Color.rgb(15, 76, 129)
    private val rule = Color.rgb(217, 222, 229)

    private val title = text(22f, ink, bold = true)
    private val heading = text(13f, accent, bold = true)
    private val body = text(10f, ink)
    private val small = text(8.5f, muted)
    private val figure = text(19f, ink, bold = true)

    private val locale: Locale get() = context.resources.configuration.locales[0]

    fun write(data: ReportData, file: File) {
        val document = PdfDocument()
        Pages(document).apply {
            header(data)
            coverageNotice(data)
            keyFigures(data)
            severities(data)
            chart(data)
            cores(data)
            incidents(data)
            method(data)
            finish()
        }
        file.outputStream().use(document::writeTo)
        document.close()
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun severityColor(severity: Severity) = when (severity) {
        Severity.CRITICAL -> Color.rgb(176, 0, 32)
        Severity.HIGH -> Color.rgb(217, 72, 15)
        Severity.MEDIUM -> Color.rgb(201, 138, 0)
        Severity.LOW -> Color.rgb(46, 125, 80)
        Severity.INFO -> Color.rgb(90, 102, 118)
    }

    private fun percent(value: Double?) =
        value?.let { "%.1f %%".format(locale, it * 100) } ?: context.getString(R.string.report_no_data)

    private fun dateTime(epoch: Long, zone: ZoneId = SPAIN_ZONE) =
        Instant.ofEpochSecond(epoch).atZone(zone)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))

    private inner class Pages(private val document: PdfDocument) {
        private var number = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private val canvas: Canvas get() = page!!.canvas
        private val width = (PAGE_WIDTH - 2 * MARGIN)

        init {
            newPage()
        }

        private fun newPage() {
            finish()
            number += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())
            y = MARGIN
            val footer = "${context.getString(R.string.report_attribution)}   ·   $number"
            canvas.drawText(footer, MARGIN, PAGE_HEIGHT - 24f, small)
        }

        fun finish() {
            page?.let(document::finishPage)
            page = null
        }

        private fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN - 12f) newPage()
        }

        private fun paragraph(value: String, paint: TextPaint = body, indent: Float = 0f, gap: Float = 6f) {
            val layout = StaticLayout.Builder
                .obtain(value, 0, value.length, paint, (width - indent).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            ensure(layout.height.toFloat())
            canvas.save()
            canvas.translate(MARGIN + indent, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + gap
        }

        private fun section(value: String) {
            ensure(60f)
            y += 12f
            paragraph(value, heading)
        }

        fun header(data: ReportData) {
            paragraph(context.getString(R.string.report_title), small, gap = 2f)
            paragraph(periodTitle(data.period, locale), title, gap = 4f)
            paragraph(context.getString(R.string.report_generated, dateTime(data.generatedAt)), small, gap = 10f)
        }

        fun coverageNotice(data: ReportData) {
            val observed = percent(data.coverage)
            paragraph(context.getString(R.string.report_coverage, observed, data.samples), body)
            if (data.coverage < LOW_COVERAGE) paragraph(context.getString(R.string.report_low_coverage), small)
        }

        fun keyFigures(data: ReportData) {
            section(context.getString(R.string.report_summary))
            val figures = listOf(
                context.getString(R.string.report_punctuality) to percent(data.punctuality),
                context.getString(R.string.report_average_delay) to
                    (data.averageDelayMinutes?.let { "%.1f min".format(locale, it) } ?: context.getString(R.string.report_no_data)),
                context.getString(R.string.report_max_delay) to "${data.maxDelayMinutes} min",
                context.getString(R.string.report_peak_cancelled) to "${data.peakCancelled}",
            )
            ensure(50f)
            val column = width / figures.size
            figures.forEachIndexed { index, (name, value) ->
                val x = MARGIN + column * index
                canvas.drawText(value, x, y + 20f, figure)
                canvas.drawText(name, x, y + 36f, small)
            }
            y += 48f
        }

        fun severities(data: ReportData) {
            section(context.getString(R.string.report_incidents_count, data.totalIncidents))
            val max = (data.bySeverity.values.maxOrNull() ?: 0).coerceAtLeast(1)
            val paint = Paint().apply { isAntiAlias = true }
            Severity.entries.reversed().forEach { severity ->
                val count = data.bySeverity[severity] ?: 0
                ensure(16f)
                canvas.drawText(label(context, severity), MARGIN, y + 10f, body)
                paint.color = severityColor(severity)
                val bar = (width - 150f) * count / max
                canvas.drawRect(MARGIN + 110f, y + 2f, MARGIN + 110f + bar.coerceAtLeast(1f), y + 12f, paint)
                canvas.drawText("$count", MARGIN + 116f + bar, y + 11f, body)
                y += 16f
            }
            if (data.unverifiedAlerts > 0) {
                y += 4f
                paragraph(context.getString(R.string.report_unverified, data.unverifiedAlerts), small)
            }
        }

        fun chart(data: ReportData) {
            if (data.daily.isEmpty()) return
            section(context.getString(R.string.report_daily))
            val height = 90f
            ensure(height + 26f)
            val max = (data.daily.maxOf { it.incidents }).coerceAtLeast(1)
            val slot = width / data.daily.size
            val paint = Paint().apply { color = accent; isAntiAlias = true }
            val base = y + height
            data.daily.forEachIndexed { index, point ->
                val bar = height * point.incidents / max
                val x = MARGIN + slot * index
                canvas.drawRect(x + slot * 0.15f, base - bar.coerceAtLeast(0.5f), x + slot * 0.85f, base, paint)
            }
            canvas.drawLine(MARGIN, base, MARGIN + width, base, Paint().apply { color = rule })
            val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
            canvas.drawText(data.daily.first().date.format(format), MARGIN, base + 12f, small)
            val last = data.daily.last().date.format(format)
            canvas.drawText(last, MARGIN + width - small.measureText(last), base + 12f, small)
            canvas.drawText("max $max", MARGIN, y + 8f, small)
            y = base + 22f
        }

        fun cores(data: ReportData) {
            if (data.byCore.isEmpty()) return
            section(context.getString(R.string.report_by_area))
            data.byCore.forEach { (core, count) ->
                val name = core?.displayName ?: context.getString(R.string.service_long_distance)
                ensure(14f)
                canvas.drawText(name, MARGIN, y + 10f, body)
                val value = "$count"
                canvas.drawText(value, MARGIN + width - body.measureText(value), y + 10f, body)
                y += 15f
            }
        }

        fun incidents(data: ReportData) {
            if (data.topIncidents.isEmpty()) return
            section(context.getString(R.string.report_top_incidents))
            data.topIncidents.forEach { incident ->
                val source = context.getString(
                    if (incident.source == IncidentSource.OFFICIAL) R.string.source_official else R.string.source_detected,
                )
                val area = incident.core?.displayName ?: context.getString(R.string.service_long_distance)
                paragraph(
                    "${label(context, incident.severity)} · $area · $source · ${dateTime(incident.firstSeen)}",
                    text(8.5f, severityColor(incident.severity), bold = true),
                    gap = 1f,
                )
                paragraph(incidentText(context, incident.source, incident.category, incident.text), body, gap = 8f)
            }
        }

        fun method(data: ReportData) {
            section(context.getString(R.string.report_method))
            val period = data.period.type == PeriodType.DAY
            paragraph(context.getString(R.string.report_method_body), small)
            if (!period) paragraph(context.getString(R.string.report_daily_note), small)
            paragraph(context.getString(R.string.report_disclaimer), small)
            paragraph(context.getString(R.string.report_attribution_full), small)
        }
    }
}
