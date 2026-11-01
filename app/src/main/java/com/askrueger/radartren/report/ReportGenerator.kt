package com.askrueger.radartren.report

import android.content.Context
import com.askrueger.radartren.data.Storage
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.data.local.RadarDatabase
import com.askrueger.radartren.data.local.ReportEntity
import com.askrueger.radartren.domain.IncidentCategory
import com.askrueger.radartren.domain.IncidentDetector
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.ReportBuilder
import com.askrueger.radartren.domain.ReportPeriod
import com.askrueger.radartren.domain.SPAIN_ZONE
import com.askrueger.radartren.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Writes the PDF of each period that just closed and has data. Periods are generated only once. */
class ReportGenerator(private val database: RadarDatabase, private val storage: Storage) {

    suspend fun generateClosedPeriods(context: Context, now: Long): List<ReportEntity> {
        val today = LocalDate.now(SPAIN_ZONE)
        return ReportPeriod.latestClosed(today).mapNotNull { period ->
            if (database.reports().exists(period.id)) null else generate(context, period, now)
        }
    }

    private suspend fun generate(context: Context, period: ReportPeriod, now: Long): ReportEntity? = withContext(Dispatchers.IO) {
        val samples = database.metrics().between(period.startEpoch, period.endEpoch).map { it.toDomain() }
        if (samples.isEmpty()) return@withContext null
        val incidents = database.incidents().overlapping(period.startEpoch, period.endEpoch).map(IncidentEntity::toReport)
        val data = ReportBuilder.build(period, samples, incidents, now)
        val file = File(storage.reportsDir, "radartren-${period.id}.pdf")
        PdfReportWriter(context).write(data, file)
        ReportEntity(period.id, period.type, period.startEpoch, now, file.name, data.coverage)
            .also { database.reports().upsert(it) }
    }
}

/** Detected incidents store figures only, so their sentence can follow the reader's language. */
fun incidentText(context: Context, source: IncidentSource, category: IncidentCategory, text: String): String = when {
    source != IncidentSource.DETECTED -> text
    category == IncidentCategory.DISRUPTION -> {
        val (delayed, total) = text.split('/')
        context.getString(R.string.incident_area_delay, delayed, total, IncidentDetector.AREA_DELAY_MINUTES)
    }
    category == IncidentCategory.CANCELLATION -> context.getString(R.string.incident_area_cancelled, text)
    else -> text
}

fun IncidentEntity.displayText(context: Context): String = incidentText(context, source, category, text)
