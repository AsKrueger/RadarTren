package com.askrueger.radartren.domain

import com.askrueger.radartren.data.remote.FeedMessage
import com.askrueger.radartren.data.remote.TripDescriptor
import com.askrueger.radartren.data.remote.TripUpdate
import com.askrueger.radartren.data.remote.VehiclePosition
import kotlin.math.abs

/** A delay beyond this is a data error: the feeds occasionally report figures like -25980 s. */
private const val MAX_PLAUSIBLE_DELAY_SECONDS = 6 * 60 * 60

/** The number at the start of a long-distance trip id such as `0581712026-10-31`. */
private const val TRAIN_NUMBER_LENGTH = 5

data class CancelledTrip(val tripId: String, val service: Service, val core: Core?)

data class NetworkSnapshot(
    val trains: List<Train>,
    val cancelled: List<CancelledTrip>,
    val health: List<FeedHealth>,
    /** Trains running according to the delay feeds, but missing from the position feeds. */
    val withoutPosition: Int = 0,
) {
    fun healthOf(feed: Feed): FeedHealth? = health.firstOrNull { it.feed == feed }

    /** True when every position and delay source is live, so absent incidents can be trusted. */
    val liveDataComplete: Boolean
        get() = health.filter { it.feed != Feed.ALERTS }.all { it.state == FeedState.LIVE }

    val officialAlertsLive: Boolean get() = healthOf(Feed.ALERTS)?.state == FeedState.LIVE

    companion object {
        val EMPTY = NetworkSnapshot(emptyList(), emptyList(), emptyList())
    }
}

/** Joins the position and delay feeds into one list of trains. */
class SnapshotAssembler(private val catalog: StaticCatalog) {

    fun assemble(
        commuterPositions: FeedMessage?,
        longDistancePositions: FeedMessage?,
        commuterUpdates: FeedMessage?,
        longDistanceUpdates: FeedMessage?,
        health: List<FeedHealth>,
    ): NetworkSnapshot {
        val commuterDelays = delays(commuterUpdates)
        val longDistanceDelays = delays(longDistanceUpdates)
        // Trip ids in long-distance feeds can carry different dates, so also match on train number.
        val longDistanceByNumber = longDistanceDelays.mapKeys { it.key.take(TRAIN_NUMBER_LENGTH) }

        val commuter = commuterPositions?.entity.orEmpty().mapNotNull { it.vehicle }.mapNotNull { vehicle ->
            commuterTrain(vehicle, commuterDelays[vehicle.trip.tripId])
        }
        val longDistance = longDistancePositions?.entity.orEmpty().mapNotNull { it.vehicle }.mapNotNull { vehicle ->
            val number = vehicle.trip.tripId.take(TRAIN_NUMBER_LENGTH)
            longDistanceTrain(vehicle, longDistanceDelays[vehicle.trip.tripId] ?: longDistanceByNumber[number])
        }.distinctBy { it.number } // The same train can appear once per trip date.

        return NetworkSnapshot(
            trains = commuter + longDistance,
            cancelled = cancelled(commuterUpdates, Service.COMMUTER) + cancelled(longDistanceUpdates, Service.LONG_DISTANCE),
            health = health,
            withoutPosition = withoutPosition(commuterDelays.keys, commuter) +
                withoutPosition(longDistanceByNumber.keys, longDistance),
        )
    }

    private fun withoutPosition(running: Set<String>, trains: List<Train>): Int {
        val located = trains.flatMap { listOf(it.id, it.number) }.toSet()
        return running.count { it !in located }
    }

    private fun commuterTrain(vehicle: VehiclePosition, delay: Int?): Train? {
        val position = vehicle.position ?: return null
        val label = vehicle.vehicle.label.substringBefore('-').takeIf(String::isNotBlank)
        return Train(
            id = vehicle.trip.tripId.ifBlank { vehicle.vehicle.id },
            service = Service.COMMUTER,
            label = label,
            number = vehicle.vehicle.id,
            latitude = position.latitude.toDouble(),
            longitude = position.longitude.toDouble(),
            status = status(vehicle),
            delayMinutes = delay,
            core = Core.ofId(vehicle.trip.tripId),
            nextStation = catalog.station(vehicle.stopId)?.name,
            origin = null,
            destination = null,
        )
    }

    private fun longDistanceTrain(vehicle: VehiclePosition, delay: Int?): Train? {
        val position = vehicle.position ?: return null
        val number = vehicle.vehicle.id
        val info = catalog.train(number)
        return Train(
            id = vehicle.trip.tripId.ifBlank { number },
            service = Service.LONG_DISTANCE,
            label = info?.product,
            number = number,
            latitude = position.latitude.toDouble(),
            longitude = position.longitude.toDouble(),
            status = status(vehicle),
            delayMinutes = delay,
            core = null,
            nextStation = catalog.station(vehicle.stopId)?.name,
            origin = info?.let { catalog.station(it.originId)?.name },
            destination = info?.let { catalog.station(it.destinationId)?.name },
        )
    }

    private fun status(vehicle: VehiclePosition) = when (vehicle.currentStatus) {
        VehiclePosition.STOPPED_AT -> TrainStatus.STOPPED
        VehiclePosition.INCOMING_AT -> TrainStatus.ARRIVING
        else -> TrainStatus.MOVING
    }

    private fun delays(updates: FeedMessage?): Map<String, Int> =
        updates?.entity.orEmpty().mapNotNull { it.tripUpdate }.mapNotNull { update ->
            delaySeconds(update)?.let { update.trip.tripId to it / 60 }
        }.toMap()

    private fun delaySeconds(update: TripUpdate): Int? {
        if (update.trip.scheduleRelationship == TripDescriptor.CANCELED) return null
        val delay = update.delay ?: update.stopTimeUpdate.firstNotNullOfOrNull { it.arrival?.delay }
        return delay?.takeIf { abs(it) <= MAX_PLAUSIBLE_DELAY_SECONDS }
    }

    private fun cancelled(updates: FeedMessage?, service: Service): List<CancelledTrip> =
        updates?.entity.orEmpty().mapNotNull { it.tripUpdate }
            .filter { it.trip.scheduleRelationship == TripDescriptor.CANCELED }
            .map {
                CancelledTrip(it.trip.tripId, service, if (service == Service.COMMUTER) Core.ofId(it.trip.tripId) else null)
            }
}
