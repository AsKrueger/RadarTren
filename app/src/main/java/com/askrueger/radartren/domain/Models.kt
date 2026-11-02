package com.askrueger.radartren.domain

import kotlin.math.roundToInt

/** Urgency of an incident, lowest first so that ordinal comparisons read naturally. */
enum class Severity {
    INFO, LOW, MEDIUM, HIGH, CRITICAL;
}

enum class Service { COMMUTER, LONG_DISTANCE }

enum class TrainStatus { STOPPED, ARRIVING, MOVING }

enum class DelayLevel {
    UNKNOWN, ON_TIME, MINOR, MODERATE, SEVERE;

    companion object {
        fun of(delayMinutes: Int?): DelayLevel = when {
            delayMinutes == null -> UNKNOWN
            delayMinutes < 5 -> ON_TIME
            delayMinutes < 15 -> MINOR
            delayMinutes < 30 -> MODERATE
            else -> SEVERE
        }
    }
}

data class Train(
    val id: String,
    val service: Service,
    /** Commuter line ("C4") or long-distance product ("AVE"). Null when the catalog lacks it. */
    val label: String?,
    val number: String,
    val latitude: Double,
    val longitude: Double,
    val status: TrainStatus,
    val delayMinutes: Int?,
    val core: Core?,
    val nextStation: String?,
    val origin: String?,
    val destination: String?,
) {
    val delayLevel: DelayLevel get() = DelayLevel.of(delayMinutes)
    val family: ProductFamily
        get() = if (service == Service.COMMUTER) ProductFamily.COMMUTER else ProductFamily.ofProduct(label)
}

/** How the map groups trains. Null [displayName]s are translated by the interface. */
enum class ProductFamily(val displayName: String?) {
    COMMUTER(null),
    AVE("AVE"),
    AVLO("Avlo"),
    AVANT("Avant"),
    ALVIA("Alvia"),
    EUROMED("Euromed"),
    INTERCITY("Intercity"),
    MEDIA_DISTANCIA("MD"),
    REGIONAL("Regional"),
    PROXIMIDAD("Proximidad"),
    OTHER(null);

    companion object {
        /** Names as Renfe spells them in its GTFS, "PROXIMDAD" included. */
        fun ofProduct(product: String?): ProductFamily = when (product?.uppercase()) {
            "AVE", "AVE INT" -> AVE
            "AVLO" -> AVLO
            "AVANT", "AVANT EXP" -> AVANT
            "ALVIA" -> ALVIA
            "EUROMED" -> EUROMED
            "INTERCITY" -> INTERCITY
            "MD" -> MEDIA_DISTANCIA
            "REGIONAL", "REG.EXP." -> REGIONAL
            "PROXIMDAD" -> PROXIMIDAD
            else -> OTHER
        }
    }
}

/** The figures under the map, for whatever trains are on it. */
data class NetworkStats(val onMap: Int, val delayed: Int, val averageDelayMinutes: Int) {
    companion object {
        fun of(trains: List<Train>): NetworkStats {
            val delays = trains.mapNotNull { it.delayMinutes }
            return NetworkStats(
                onMap = trains.size,
                delayed = delays.count { it >= 5 },
                averageDelayMinutes = if (delays.isEmpty()) 0 else delays.map { it.coerceAtLeast(0) }.average().roundToInt(),
            )
        }
    }
}

/** A Cercanías/Rodalies network. The code is the two-digit prefix of Renfe route and trip ids. */
enum class Core(val code: String, val displayName: String, val latitude: Double, val longitude: Double) {
    // Coordinates are the main city of each network, where its incidents are drawn on the map.
    MADRID("10", "Madrid", 40.4168, -3.7038),
    ASTURIAS("20", "Asturias", 43.3614, -5.8494),
    SEVILLA("30", "Sevilla", 37.3891, -5.9845),
    CADIZ("31", "Cádiz", 36.5271, -6.2886),
    MALAGA("32", "Málaga", 36.7213, -4.4214),
    VALENCIA("40", "València", 39.4699, -0.3763),
    MURCIA_ALICANTE("41", "Murcia/Alicante", 38.3452, -0.4810),
    CARTAGENA("45", "Cartagena", 37.6257, -0.9966),
    FERROL("46", "Ferrol", 43.4832, -8.2369),
    LEON("47", "León", 42.5987, -5.5671),
    RODALIES("51", "Rodalies de Catalunya", 41.3874, 2.1686),
    BILBAO("60", "Bilbao", 43.2630, -2.9350),
    SAN_SEBASTIAN("61", "San Sebastián", 43.3183, -1.9812),
    SANTANDER("62", "Santander", 43.4623, -3.8100),
    ZARAGOZA("70", "Zaragoza", 41.6488, -0.8891);

    companion object {
        fun ofId(id: String): Core? = entries.firstOrNull { id.startsWith(it.code) }
    }
}

/** Where an incident came from. Decides how much the app may trust and announce it. */
enum class IncidentSource {
    /** Published by Renfe in its alerts feed. */
    OFFICIAL,

    /** Computed by this app from live delays and cancellations. Never a confirmed cause. */
    DETECTED,
}

enum class IncidentCategory {
    SAFETY, SERVICE_INTERRUPTED, DISRUPTION, DELAY, CANCELLATION, PLANNED_WORKS, ACCESSIBILITY, INFORMATION
}

/** What a collection run observes; persisting it turns it into a tracked incident. */
data class IncidentDraft(
    val key: String,
    val source: IncidentSource,
    val severity: Severity,
    val category: IncidentCategory,
    val core: Core?,
    val lines: List<String>,
    val text: String,
    val startedAt: Long,
    /** Where to draw it: the train itself, the stop named by a notice, or the network's city. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)
