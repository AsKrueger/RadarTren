package com.askrueger.radartren.domain

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
}

/** A Cercanías/Rodalies network. The code is the two-digit prefix of Renfe route and trip ids. */
enum class Core(val code: String, val displayName: String) {
    MADRID("10", "Madrid"),
    ASTURIAS("20", "Asturias"),
    SEVILLA("30", "Sevilla"),
    CADIZ("31", "Cádiz"),
    MALAGA("32", "Málaga"),
    VALENCIA("40", "València"),
    MURCIA_ALICANTE("41", "Murcia/Alicante"),
    CARTAGENA("45", "Cartagena"),
    FERROL("46", "Ferrol"),
    LEON("47", "León"),
    RODALIES("51", "Rodalies de Catalunya"),
    BILBAO("60", "Bilbao"),
    SAN_SEBASTIAN("61", "San Sebastián"),
    SANTANDER("62", "Santander"),
    ZARAGOZA("70", "Zaragoza");

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
)
