package com.askrueger.radartren.domain

enum class Feed(val path: String, val maxAgeSeconds: Long) {
    COMMUTER_POSITIONS("vehicle_positions.pb", 5 * 60),
    LONG_DISTANCE_POSITIONS("vehicle_positions_LD.pb", 5 * 60),
    COMMUTER_UPDATES("trip_updates.pb", 5 * 60),
    LONG_DISTANCE_UPDATES("trip_updates_LD.pb", 5 * 60),

    /** Notices are edited by people, so a quiet hour is normal; six hours is not. */
    ALERTS("alerts.pb", 6 * 60 * 60),
}

enum class FeedState {
    LIVE,

    /** Answers 200 but its content stopped changing. It must not be presented as current. */
    FROZEN,

    /** The request failed. */
    UNREACHABLE,
}

data class FeedHealth(
    val feed: Feed,
    val state: FeedState,
    /** When the server last changed the file, in epoch seconds. */
    val updatedAt: Long?,
) {
    companion object {
        /**
         * Judges freshness from the HTTP `Last-Modified` and `Date` headers, both set by the same
         * server, which also makes the result independent of the phone clock.
         *
         * The GTFS header timestamp is not used: on `vehicle_positions_LD` it runs an hour behind
         * while the positions are live (see docs/fuentes-de-datos.md).
         */
        fun evaluate(feed: Feed, lastModified: Long?, serverNow: Long): FeedHealth {
            val state = when {
                lastModified == null -> FeedState.FROZEN
                serverNow - lastModified > feed.maxAgeSeconds -> FeedState.FROZEN
                else -> FeedState.LIVE
            }
            return FeedHealth(feed, state, lastModified)
        }

        fun unreachable(feed: Feed, updatedAt: Long?) = FeedHealth(feed, FeedState.UNREACHABLE, updatedAt)
    }
}
