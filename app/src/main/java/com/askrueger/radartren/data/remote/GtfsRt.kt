@file:OptIn(ExperimentalSerializationApi::class)

package com.askrueger.radartren.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * The subset of the GTFS Realtime schema that Renfe fills in. Field numbers come from the official
 * gtfs-realtime.proto; unknown fields are skipped by the decoder.
 *
 * The enums stay as plain Ints so that a value added upstream cannot break the whole decode.
 */
@Serializable
data class FeedMessage(
    @ProtoNumber(1) val header: FeedHeader = FeedHeader(),
    @ProtoNumber(2) val entity: List<FeedEntity> = emptyList(),
) {
    companion object {
        private val proto = ProtoBuf

        fun decode(bytes: ByteArray): FeedMessage = proto.decodeFromByteArray(bytes)
    }
}

@Serializable
data class FeedHeader(
    @ProtoNumber(3) val timestamp: Long = 0,
)

@Serializable
data class FeedEntity(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(3) val tripUpdate: TripUpdate? = null,
    @ProtoNumber(4) val vehicle: VehiclePosition? = null,
    @ProtoNumber(5) val alert: Alert? = null,
)

@Serializable
data class TripDescriptor(
    @ProtoNumber(1) val tripId: String = "",
    @ProtoNumber(4) val scheduleRelationship: Int = SCHEDULED,
    @ProtoNumber(5) val routeId: String = "",
) {
    companion object {
        const val SCHEDULED = 0
        const val CANCELED = 3
    }
}

@Serializable
data class TripUpdate(
    @ProtoNumber(1) val trip: TripDescriptor = TripDescriptor(),
    @ProtoNumber(2) val stopTimeUpdate: List<StopTimeUpdate> = emptyList(),
    @ProtoNumber(5) val delay: Int? = null,
)

@Serializable
data class StopTimeUpdate(
    @ProtoNumber(2) val arrival: StopTimeEvent? = null,
    @ProtoNumber(4) val stopId: String = "",
)

@Serializable
data class StopTimeEvent(
    @ProtoNumber(1) val delay: Int? = null,
)

@Serializable
data class VehiclePosition(
    @ProtoNumber(1) val trip: TripDescriptor = TripDescriptor(),
    @ProtoNumber(2) val position: Position? = null,
    @ProtoNumber(4) val currentStatus: Int = IN_TRANSIT_TO,
    @ProtoNumber(5) val timestamp: Long = 0,
    @ProtoNumber(7) val stopId: String = "",
    @ProtoNumber(8) val vehicle: VehicleDescriptor = VehicleDescriptor(),
) {
    companion object {
        const val INCOMING_AT = 0
        const val STOPPED_AT = 1
        const val IN_TRANSIT_TO = 2
    }
}

@Serializable
data class Position(
    @ProtoNumber(1) val latitude: Float = 0f,
    @ProtoNumber(2) val longitude: Float = 0f,
)

@Serializable
data class VehicleDescriptor(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(2) val label: String = "",
)

@Serializable
data class Alert(
    @ProtoNumber(1) val activePeriod: List<TimeRange> = emptyList(),
    @ProtoNumber(5) val informedEntity: List<EntitySelector> = emptyList(),
    @ProtoNumber(11) val descriptionText: TranslatedString = TranslatedString(),
)

@Serializable
data class TimeRange(
    @ProtoNumber(1) val start: Long = 0,
    @ProtoNumber(2) val end: Long = 0,
)

@Serializable
data class EntitySelector(
    @ProtoNumber(2) val routeId: String = "",
    @ProtoNumber(5) val stopId: String = "",
)

@Serializable
data class TranslatedString(
    @ProtoNumber(1) val translation: List<Translation> = emptyList(),
)

@Serializable
data class Translation(
    @ProtoNumber(1) val text: String = "",
    @ProtoNumber(2) val language: String = "",
)
