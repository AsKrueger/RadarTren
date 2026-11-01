package com.askrueger.radartren.data

import com.askrueger.radartren.data.remote.FeedMessage
import com.askrueger.radartren.data.remote.FeedResult
import com.askrueger.radartren.data.remote.RenfeClient
import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.NetworkSnapshot
import com.askrueger.radartren.domain.SnapshotAssembler
import com.askrueger.radartren.domain.StaticCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LiveData(val snapshot: NetworkSnapshot, val alerts: FeedMessage?, val fetchedAt: Long)

/** Fetches every feed and keeps the latest view of the network in memory. */
class LiveRepository(private val client: RenfeClient, private val catalog: StaticCatalog) {
    private val assembler = SnapshotAssembler(catalog)
    private val _live = MutableStateFlow<LiveData?>(null)
    val live: StateFlow<LiveData?> = _live

    suspend fun refresh(): LiveData = coroutineScope {
        val results = Feed.entries.map { async { client.fetch(it) } }.awaitAll().associateBy(FeedResult::feed)
        val snapshot = assembler.assemble(
            commuterPositions = results[Feed.COMMUTER_POSITIONS]?.message,
            longDistancePositions = results[Feed.LONG_DISTANCE_POSITIONS]?.message,
            commuterUpdates = results[Feed.COMMUTER_UPDATES]?.message,
            longDistanceUpdates = results[Feed.LONG_DISTANCE_UPDATES]?.message,
            health = results.values.map(FeedResult::health),
        )
        val previous = _live.value
        // Keep the last trains on screen through a failed refresh; the health says they are old.
        val merged = if (snapshot.trains.isEmpty() && previous != null) {
            previous.snapshot.copy(health = snapshot.health)
        } else {
            snapshot
        }
        val alerts = results[Feed.ALERTS]?.message ?: previous?.alerts
        val fetched = if (snapshot.trains.isEmpty() && previous != null) previous.fetchedAt else System.currentTimeMillis() / 1000
        LiveData(merged, alerts, fetched).also { _live.value = it }
    }
}
