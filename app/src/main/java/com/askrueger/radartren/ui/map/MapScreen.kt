package com.askrueger.radartren.ui.map

import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.askrueger.radartren.R
import com.askrueger.radartren.data.LiveData
import com.askrueger.radartren.data.MAP_STYLE_URL
import com.askrueger.radartren.domain.DelayLevel
import com.askrueger.radartren.domain.Service
import com.askrueger.radartren.domain.Train
import com.askrueger.radartren.domain.TrainStatus
import com.askrueger.radartren.ui.common.delayColorHex
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val SOURCE = "trains"
private const val DOTS = "train-dots"
private const val LABELS = "train-labels"
private const val LABEL_MIN_ZOOM = 9f

private enum class ServiceFilter { ALL, COMMUTER, LONG_DISTANCE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(live: LiveData?, modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableStateOf(ServiceFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }

    val trains = remember(live, filter, query) {
        val needle = query.trim().lowercase()
        live?.snapshot?.trains.orEmpty().filter { train ->
            val inService = when (filter) {
                ServiceFilter.ALL -> true
                ServiceFilter.COMMUTER -> train.service == Service.COMMUTER
                ServiceFilter.LONG_DISTANCE -> train.service == Service.LONG_DISTANCE
            }
            inService && (needle.isEmpty() || listOfNotNull(train.label, train.number, train.destination, train.core?.displayName)
                .any { needle in it.lowercase() })
        }
    }
    val selected = live?.snapshot?.trains?.firstOrNull { it.id == selectedId }

    Box(modifier.fillMaxSize()) {
        TrainMap(trains, onTrainClick = { selectedId = it }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.map_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ServiceFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        ServiceFilter.ALL -> R.string.filter_all
                                        ServiceFilter.COMMUTER -> R.string.service_commuter
                                        ServiceFilter.LONG_DISTANCE -> R.string.service_long_distance
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        // Lifted so that it never covers the map attribution, which the licences require to be visible.
        Legend(trains.size, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 40.dp))
    }

    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { selectedId = null }) { TrainDetails(selected) }
    }
}

@Composable
private fun TrainMap(trains: List<Train>, onTrainClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onClick by rememberUpdatedState(onTrainClick)
    var loaded by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder().target(LatLng(40.0, -3.7)).zoom(4.8).build()
                map.addOnMapClickListener { point ->
                    val screen: PointF = map.projection.toScreenLocation(point)
                    val hit = map.queryRenderedFeatures(screen, DOTS).firstOrNull()
                    hit?.getStringProperty("id")?.let { onClick(it) }
                    hit != null
                }
                map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                    style.addSource(GeoJsonSource(SOURCE))
                    style.addLayer(
                        CircleLayer(DOTS, SOURCE).withProperties(
                            PropertyFactory.circleColor(Expression.get("color")),
                            PropertyFactory.circleRadius(
                                Expression.interpolate(
                                    Expression.linear(), Expression.zoom(),
                                    Expression.stop(4, 3f), Expression.stop(8, 5f), Expression.stop(13, 9f),
                                ),
                            ),
                            PropertyFactory.circleStrokeWidth(1.2f),
                            PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                        ),
                    )
                    style.addLayer(
                        SymbolLayer(LABELS, SOURCE).withProperties(
                            PropertyFactory.textField(Expression.get("label")),
                            PropertyFactory.textSize(11f),
                            PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
                            PropertyFactory.textHaloColor(Color.White.toArgb()),
                            PropertyFactory.textHaloWidth(1.4f),
                            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                        ).apply { minZoom = LABEL_MIN_ZOOM },
                    )
                    loaded = map to style
                }
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(loaded, trains) {
        val source = loaded?.second?.getSourceAs<GeoJsonSource>(SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                trains.map { train ->
                    Feature.fromGeometry(Point.fromLngLat(train.longitude, train.latitude)).apply {
                        addStringProperty("id", train.id)
                        addStringProperty("color", delayColorHex(train.delayLevel))
                        addStringProperty("label", train.label ?: train.number)
                    }
                },
            ),
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

@Composable
private fun Legend(count: Int, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.map_trains_count, count), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            LegendRow(DelayLevel.ON_TIME, R.string.legend_on_time)
            LegendRow(DelayLevel.MINOR, R.string.legend_minor)
            LegendRow(DelayLevel.MODERATE, R.string.legend_moderate)
            LegendRow(DelayLevel.SEVERE, R.string.legend_severe)
            LegendRow(DelayLevel.UNKNOWN, R.string.legend_unknown)
        }
    }
}

@Composable
private fun LegendRow(level: DelayLevel, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(9.dp).background(Color(android.graphics.Color.parseColor(delayColorHex(level))), CircleShape),
        )
        Spacer(Modifier.size(6.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TrainDetails(train: Train) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Text(
            "${train.label.orEmpty()} ${train.number}".trim(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            train.core?.displayName ?: stringResource(R.string.service_long_distance),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Detail(
            stringResource(R.string.train_delay),
            when (val delay = train.delayMinutes) {
                null -> stringResource(R.string.train_delay_unknown)
                in Int.MIN_VALUE..0 -> stringResource(R.string.train_on_time)
                else -> stringResource(R.string.train_delay_minutes, delay)
            },
        )
        Detail(
            stringResource(R.string.train_status),
            stringResource(
                when (train.status) {
                    TrainStatus.STOPPED -> R.string.status_stopped
                    TrainStatus.ARRIVING -> R.string.status_arriving
                    TrainStatus.MOVING -> R.string.status_moving
                },
            ),
        )
        train.nextStation?.let { Detail(stringResource(R.string.train_next_station), it) }
        if (train.origin != null && train.destination != null) {
            Detail(stringResource(R.string.train_route), "${train.origin} → ${train.destination}")
        }
    }
}

@Composable
private fun Detail(name: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/** Shown over every tab when the picture is incomplete, so nobody mistakes old data for live data. */
@Composable
fun DataWarning(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.size(10.dp))
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}
