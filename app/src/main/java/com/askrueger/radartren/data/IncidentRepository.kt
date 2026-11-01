package com.askrueger.radartren.data

import androidx.room.withTransaction
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.data.local.MetricSampleEntity
import com.askrueger.radartren.data.local.RadarDatabase
import com.askrueger.radartren.domain.IncidentDetector
import com.askrueger.radartren.domain.IncidentDraft
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.NetworkSnapshot
import com.askrueger.radartren.domain.SAMPLE_INTERVAL_SECONDS
import com.askrueger.radartren.domain.StaticCatalog

/** Two years, enough to build the yearly report of the previous calendar year at any moment. */
private const val RETENTION_SECONDS = 2 * 366 * 86_400L

/** WorkManager wakes up a little early or late; this keeps such a run from being skipped. */
private const val SAMPLE_TOLERANCE_SECONDS = 120L

/** Stores what each collection saw and works out which incidents are new, worse or over. */
class IncidentRepository(private val database: RadarDatabase, private val catalog: StaticCatalog) {
    private val incidents = database.incidents()

    /** Returns the incidents worth announcing: verified ones that are new or got worse. */
    suspend fun record(live: LiveData, now: Long): List<IncidentEntity> = database.withTransaction {
        val snapshot = live.snapshot
        val detected = if (snapshot.liveDataComplete) IncidentDetector.detect(snapshot, now) else emptyList()
        val official = IncidentDetector.officialAlerts(live.alerts, catalog)
        val alertsVerified = snapshot.officialAlertsLive

        val announce = detected.mapNotNull { merge(it, verified = true, now) } +
            official.mapNotNull { merge(it, verified = alertsVerified, now) }

        val seen = (detected + official).map { it.key }.toSet()
        // A failed fetch proves nothing, so each source only closes what it could actually see.
        val stale = incidents.open().filter { open ->
            open.key !in seen && when (open.source) {
                IncidentSource.DETECTED -> snapshot.liveDataComplete
                IncidentSource.OFFICIAL -> alertsVerified
            }
        }
        if (stale.isNotEmpty()) incidents.resolve(stale.map { it.key }, now)

        // Samples feed the reports, so they keep the 15-minute pace however often the app refreshes,
        // and a partial picture of the network is not one: it would overstate coverage.
        val last = database.metrics().latest() ?: 0
        if (snapshot.liveDataComplete && now - last >= SAMPLE_INTERVAL_SECONDS - SAMPLE_TOLERANCE_SECONDS) {
            database.metrics().insert(sample(snapshot, now))
        }
        incidents.deleteOlderThan(now - RETENTION_SECONDS)
        database.metrics().deleteOlderThan(now - RETENTION_SECONDS)
        announce
    }

    private suspend fun merge(draft: IncidentDraft, verified: Boolean, now: Long): IncidentEntity? {
        val existing = incidents.find(draft.key)
        val merged = IncidentEntity(
            key = draft.key,
            source = draft.source,
            severity = maxOf(draft.severity, existing?.severity ?: draft.severity),
            category = draft.category,
            coreCode = draft.core?.code,
            lines = draft.lines.joinToString(IncidentEntity.LINE_SEPARATOR),
            text = draft.text,
            startedAt = existing?.startedAt ?: draft.startedAt,
            firstSeen = existing?.firstSeen ?: now,
            lastSeen = now,
            resolvedAt = null,
            verified = verified || existing?.verified == true,
        )
        incidents.upsert(merged)
        val isNews = existing == null || existing.resolvedAt != null || merged.severity > existing.severity
        return merged.takeIf { isNews && verified }
    }

    private fun sample(snapshot: NetworkSnapshot, now: Long): MetricSampleEntity {
        val delays = snapshot.trains.mapNotNull { it.delayMinutes }
        return MetricSampleEntity(
            at = now,
            trains = snapshot.trains.size,
            withDelayInfo = delays.size,
            delayed5 = delays.count { it >= 5 },
            delayed15 = delays.count { it >= 15 },
            delaySumMinutes = delays.sumOf { it.coerceAtLeast(0) },
            maxDelayMinutes = delays.maxOrNull() ?: 0,
            cancelled = snapshot.cancelled.size,
        )
    }
}
