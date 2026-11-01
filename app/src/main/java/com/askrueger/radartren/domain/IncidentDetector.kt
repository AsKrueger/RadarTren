package com.askrueger.radartren.domain

import com.askrueger.radartren.data.remote.FeedMessage

/**
 * Turns one snapshot into incident drafts. Thresholds were calibrated against live feeds on
 * 2026-11-01 (docs/clasificacion-de-urgencia.md) so that a normal day raises few high alerts.
 *
 * Detected incidents state what the data shows, never a cause: a delay is not proof of a fault.
 */
object IncidentDetector {
    const val TRAIN_DELAY_MEDIUM_MINUTES = 30
    const val TRAIN_DELAY_HIGH_MINUTES = 120
    const val AREA_DELAY_MINUTES = 15

    /** Below this many running trains, a percentage of delays says nothing about a network. */
    const val AREA_MIN_TRAINS = 8
    const val AREA_MEDIUM_RATIO = 0.25
    const val AREA_HIGH_RATIO = 0.40
    const val AREA_MIN_CANCELLED = 3
    const val AREA_CANCELLED_MEDIUM_RATIO = 0.10
    const val AREA_CANCELLED_HIGH_RATIO = 0.25

    fun detect(snapshot: NetworkSnapshot, now: Long): List<IncidentDraft> = buildList {
        addAll(trainDelays(snapshot, now))
        addAll(areaDelays(snapshot, now))
        addAll(areaCancellations(snapshot, now))
    }

    private fun trainDelays(snapshot: NetworkSnapshot, now: Long) = snapshot.trains.mapNotNull { train ->
        val delay = train.delayMinutes ?: return@mapNotNull null
        val severity = when {
            delay >= TRAIN_DELAY_HIGH_MINUTES -> Severity.HIGH
            delay >= TRAIN_DELAY_MEDIUM_MINUTES -> Severity.MEDIUM
            else -> return@mapNotNull null
        }
        IncidentDraft(
            key = "train:${train.service}:${train.number}",
            source = IncidentSource.DETECTED,
            severity = severity,
            category = IncidentCategory.DELAY,
            core = train.core,
            lines = listOfNotNull(train.label),
            text = "${train.label.orEmpty()} ${train.number}".trim() + ": +$delay min",
            startedAt = now,
        )
    }

    private fun areaDelays(snapshot: NetworkSnapshot, now: Long) = areas(snapshot).mapNotNull { (area, trains) ->
        val known = trains.filter { it.delayMinutes != null }
        if (known.size < AREA_MIN_TRAINS) return@mapNotNull null
        val delayed = known.count { it.delayMinutes!! >= AREA_DELAY_MINUTES }
        val ratio = delayed.toDouble() / known.size
        val severity = when {
            ratio >= AREA_HIGH_RATIO -> Severity.HIGH
            ratio >= AREA_MEDIUM_RATIO -> Severity.MEDIUM
            else -> return@mapNotNull null
        }
        IncidentDraft(
            key = "area-delay:${area?.code ?: "LD"}",
            source = IncidentSource.DETECTED,
            severity = severity,
            category = IncidentCategory.DISRUPTION,
            core = area,
            lines = emptyList(),
            text = "$delayed/${known.size}",
            startedAt = now,
        )
    }

    /**
     * Relative to the size of each network: the long-distance feed lists a dozen cancelled trips on
     * an ordinary day, while the same figure would paralyse a small commuter network.
     */
    private fun areaCancellations(snapshot: NetworkSnapshot, now: Long): List<IncidentDraft> {
        val running = areas(snapshot)
        return snapshot.cancelled.groupBy { it.core }.mapNotNull { (area, trips) ->
            val ratio = trips.size.toDouble() / (trips.size + running[area].orEmpty().size)
            val severity = when {
                trips.size < AREA_MIN_CANCELLED -> return@mapNotNull null
                ratio >= AREA_CANCELLED_HIGH_RATIO -> Severity.HIGH
                ratio >= AREA_CANCELLED_MEDIUM_RATIO -> Severity.MEDIUM
                else -> return@mapNotNull null
            }
            IncidentDraft(
                key = "area-cancelled:${area?.code ?: "LD"}",
                source = IncidentSource.DETECTED,
                severity = severity,
                category = IncidentCategory.CANCELLATION,
                core = area,
                lines = emptyList(),
                text = "${trips.size}",
                startedAt = now,
            )
        }
    }

    /** Commuter trains group by network; every long-distance train shares the null area. */
    private fun areas(snapshot: NetworkSnapshot): Map<Core?, List<Train>> = snapshot.trains.groupBy { it.core }

    /** Official notices as drafts. Renfe repeats a notice per network, so copies merge their lines. */
    fun officialAlerts(alerts: FeedMessage?, catalog: StaticCatalog): List<IncidentDraft> =
        alerts?.entity.orEmpty().mapNotNull { entity ->
            val alert = entity.alert ?: return@mapNotNull null
            val raw = alert.descriptionText.translation.firstOrNull()?.text.orEmpty()
            val classification = AlertClassifier.classify(raw) ?: return@mapNotNull null
            val text = AlertClassifier.readableText(raw)
            val routes = alert.informedEntity.map { it.routeId.trim() }.filter(String::isNotEmpty)
            IncidentDraft(
                key = "alert:${entity.id}:${text.hashCode()}",
                source = IncidentSource.OFFICIAL,
                severity = classification.severity,
                category = classification.category,
                core = routes.firstNotNullOfOrNull(Core::ofId),
                lines = routes.mapNotNull { catalog.line(it)?.name }.distinct(),
                text = text,
                startedAt = alert.activePeriod.firstOrNull()?.start ?: 0,
            )
        }.groupBy { it.key }.values.map { copies ->
            copies.first().copy(lines = copies.flatMap { it.lines }.distinct())
        }
}
