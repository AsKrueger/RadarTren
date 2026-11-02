package com.askrueger.radartren.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

private const val LIGHT_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

fun mapStyleUrl(dark: Boolean): String = if (dark) DARK_STYLE else LIGHT_STYLE

sealed interface OfflineMapState {
    data object Idle : OfflineMapState
    data class Downloading(val percent: Int) : OfflineMapState
    data object Ready : OfflineMapState
    data object Failed : OfflineMapState
}

/**
 * Saves the map of Spain for offline use. Zoom stops at 8 to respect the OpenFreeMap limits of
 * 3,000 tiles and 50 MB per download: zoom 9 fits the tiles but measured about 58 MB
 * (docs/arquitectura.md). The map engine still draws these tiles when zooming in further.
 */
class OfflineMaps(private val context: Context) {
    private val manager get() = OfflineManager.getInstance(context)
    private val _state = MutableStateFlow<OfflineMapState>(OfflineMapState.Idle)
    val state: StateFlow<OfflineMapState> = _state

    /** Saves both styles so that the theme can change offline. They share tiles, so the size barely grows. */
    suspend fun enable() {
        val saved = regions().map { String(it.metadata) }
        val missing = listOf(LIGHT_STYLE, DARK_STYLE).filter { it !in saved }
        _state.value = OfflineMapState.Downloading(0)
        missing.forEachIndexed { index, style ->
            if (!download(style) { percent -> (index * 100 + percent) / missing.size }) {
                _state.value = OfflineMapState.Failed
                return
            }
        }
        _state.value = OfflineMapState.Ready
    }

    private suspend fun download(style: String, overall: (Int) -> Int): Boolean {
        val definition = OfflineTilePyramidRegionDefinition(
            style,
            LatLngBounds.from(44.0, 4.6, 27.4, -18.5),
            MIN_ZOOM,
            MAX_ZOOM,
            context.resources.displayMetrics.density,
        )
        val created = CompletableDeferred<OfflineRegion>()
        manager.createOfflineRegion(
            definition,
            style.toByteArray(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) { created.complete(offlineRegion) }
                override fun onError(error: String) { created.completeExceptionally(IllegalStateException(error)) }
            },
        )
        val region = try {
            created.await()
        } catch (_: IllegalStateException) {
            return false
        }
        val finished = CompletableDeferred<Boolean>()
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val required = status.requiredResourceCount.coerceAtLeast(1)
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    finished.complete(true)
                } else {
                    _state.value = OfflineMapState.Downloading(overall((status.completedResourceCount * 100 / required).toInt()))
                }
            }

            // Transient network errors are retried by the engine.
            override fun onError(error: OfflineRegionError) = Unit

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                finished.complete(false)
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        return finished.await()
    }

    suspend fun disable() {
        regions().forEach { region ->
            val done = CompletableDeferred<Unit>()
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() { done.complete(Unit) }
                override fun onError(error: String) { done.complete(Unit) }
            })
            done.await()
        }
        _state.value = OfflineMapState.Idle
    }

    suspend fun clearViewedTiles() {
        val done = CompletableDeferred<Unit>()
        manager.clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() { done.complete(Unit) }
            override fun onError(message: String) { done.complete(Unit) }
        })
        done.await()
    }

    private suspend fun regions(): List<OfflineRegion> {
        val result = CompletableDeferred<List<OfflineRegion>>()
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) { result.complete(offlineRegions.orEmpty().toList()) }
            override fun onError(error: String) { result.complete(emptyList()) }
        })
        return result.await()
    }

    private companion object {
        const val MIN_ZOOM = 3.0
        const val MAX_ZOOM = 8.0
    }
}
