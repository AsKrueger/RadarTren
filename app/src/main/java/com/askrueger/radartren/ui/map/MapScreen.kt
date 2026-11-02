package com.askrueger.radartren.ui.map

import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.askrueger.radartren.R
import com.askrueger.radartren.data.LiveData
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.data.mapStyleUrl
import com.askrueger.radartren.domain.DelayLevel
import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.NetworkStats
import com.askrueger.radartren.domain.ProductFamily
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.domain.Station
import com.askrueger.radartren.domain.Train
import com.askrueger.radartren.domain.TrainStatus
import com.askrueger.radartren.report.displayText
import com.askrueger.radartren.ui.common.categoryLabel
import com.askrueger.radartren.ui.common.clock
import com.askrueger.radartren.ui.common.delayColorHex
import com.askrueger.radartren.ui.common.durationText
import com.askrueger.radartren.ui.help.HelpSheet
import com.askrueger.radartren.ui.common.label
import com.askrueger.radartren.ui.common.severityColor
import com.askrueger.radartren.ui.common.severityColorHex
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.sin

private const val TRAINS = "trains"
private const val INCIDENTS = "incidents"
private const val RINGS = "incident-rings"
private const val FOCUS_ZOOM = 10.0
private const val PULSE_FRAME_MILLIS = 80L
private const val NETWORK = "network"
private const val STATIONS = "stations"
private const val DOTS = "train-dots"
private const val LABELS = "train-labels"
private const val LABEL_MIN_ZOOM = 8.5f
private const val STATION_MIN_ZOOM = 7f
private val SPAIN = LatLng(40.0, -3.7)
private const val SPAIN_ZOOM = 4.8

/** Colors drawn by the map engine, which sit outside the Compose theme. */
private class MapColors(dark: Boolean) {
    val highSpeed = if (dark) "#6C93C4" else "#0F4C81"
    val conventional = if (dark) "#5E7591" else "#8FA0B3"
    val halo = if (dark) "#0E1621" else "#FFFFFF"
    val text = if (dark) "#E8EDF3" else "#18202C"
    val station = if (dark) "#C4D0DE" else "#3A4656"
}

/** A request to center the map on an incident, sent from the incidents list or a snackbar. */
data class MapFocus(val latitude: Double, val longitude: Double, val incidentKey: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    live: LiveData?,
    stations: List<Station>,
    incidents: List<IncidentEntity>,
    focus: MapFocus?,
    onFocusShown: () -> Unit,
    onOpenIncidents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var family by rememberSaveable { mutableStateOf<ProductFamily?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedIncident by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val onMap = remember(incidents) { incidents.filter { it.hasLocation && it.severity >= Severity.MEDIUM } }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val all = live?.snapshot?.trains.orEmpty()
    val counts = remember(all) { all.groupingBy { it.family }.eachCount() }
    val trains = remember(all, family, query) {
        val needle = fold(query.trim())
        all.filter { train ->
            (family == null || train.family == family) &&
                (needle.isEmpty() || train.searchTerms().any { needle in it })
        }
    }
    val selected = all.firstOrNull { it.id == selectedId }
    val incident = incidents.firstOrNull { it.key == selectedIncident }

    LaunchedEffect(focus, map) {
        val target = focus ?: return@LaunchedEffect
        val ready = map ?: return@LaunchedEffect
        ready.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), FOCUS_ZOOM))
        selectedIncident = target.incidentKey
        onFocusShown()
    }

    Box(modifier.fillMaxSize()) {
        key(dark) {
            TrainMap(
                trains = trains,
                stations = stations,
                incidents = onMap,
                dark = dark,
                onReady = { map = it },
                onTrainClick = { selectedId = it },
                onIncidentClick = { selectedIncident = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Panel {
                Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.app_name),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(onClick = { help = true }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.map_help))
                        }
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        }) {
                            Icon(
                                if (searching) Icons.Default.Close else Icons.Default.Search,
                                stringResource(R.string.map_search),
                            )
                        }
                    }
                    Status(live)
                    if (searching) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 10.dp),
                            placeholder = { Text(stringResource(R.string.map_search)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
            }
            FamilyChips(counts, all.size, family) { family = it }
            if (onMap.isNotEmpty() || incidents.isNotEmpty()) {
                IncidentPill(incidents, onOpenIncidents)
            }
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapButton(Icons.Default.Add, R.string.map_zoom_in) { map?.animateCamera(CameraUpdateFactory.zoomIn()) }
            MapButton(Icons.Default.Remove, R.string.map_zoom_out) { map?.animateCamera(CameraUpdateFactory.zoomOut()) }
            MapButton(Icons.Default.MyLocation, R.string.map_recenter) {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(SPAIN, SPAIN_ZOOM))
            }
        }
        // Lifted so that it never covers the map attribution, which the licences require to be visible.
        Summary(
            NetworkStats.of(trains),
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 34.dp),
        )
    }

    if (selected != null) {
        ModalBottomSheet(onDismissRequest = { selectedId = null }) { TrainDetails(selected) }
    }
    if (incident != null) {
        ModalBottomSheet(onDismissRequest = { selectedIncident = null }) { IncidentDetails(incident) }
    }
    if (help) HelpSheet { help = false }
}

/**
 * What people may type: line, number, the station it is at or going to, origin, destination or
 * network. Accents are ignored, so "Atocha" finds "Madrid-Puerta de Atocha".
 */
private fun Train.searchTerms(): List<String> =
    listOfNotNull(label, number, nextStation, origin, destination, core?.displayName).map(::fold)

private fun fold(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

@Composable
private fun IncidentPill(incidents: List<IncidentEntity>, onClick: () -> Unit) {
    val notable = incidents.filter { it.severity >= Severity.MEDIUM }
    if (notable.isEmpty()) return
    val worst = notable.maxOf { it.severity }
    Panel(Modifier.padding(top = 6.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(severityColor(worst), CircleShape))
            Spacer(Modifier.size(8.dp))
            Text(
                pluralStringResource(R.plurals.map_active_incidents, notable.size, notable.size),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun IncidentDetails(incident: IncidentEntity) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(severityColor(incident.severity), CircleShape))
            Spacer(Modifier.size(10.dp))
            Text(
                "${label(context, incident.severity)} · ${categoryLabel(context, incident.category)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(incident.displayText(context), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(14.dp))
        Detail(stringResource(R.string.incident_area), incident.core?.displayName ?: stringResource(R.string.service_long_distance))
        Detail(
            stringResource(R.string.incident_source),
            stringResource(if (incident.source == IncidentSource.OFFICIAL) R.string.source_official else R.string.source_detected),
        )
        Detail(stringResource(R.string.incident_first_seen), clock(incident.firstSeen))
        Detail(stringResource(R.string.incident_last_seen), clock(incident.lastSeen, seconds = true))
        if (incident.source == IncidentSource.DETECTED) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.incident_detected_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
        shadowElevation = 3.dp,
        content = content,
    )
}

@Composable
private fun Status(live: LiveData?) {
    val snapshot = live?.snapshot
    val isLive = snapshot?.liveDataComplete == true
    val updatedAt = snapshot?.healthOf(Feed.COMMUTER_POSITIONS)?.updatedAt
    val parts = buildList {
        when {
            snapshot == null -> add(stringResource(R.string.map_status_loading))
            isLive && updatedAt != null -> {
                val time = Instant.ofEpochSecond(updatedAt).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                add(stringResource(R.string.map_status_live, time))
            }
            else -> add(stringResource(R.string.map_status_stale))
        }
        if (isLive && snapshot.withoutPosition > 0) {
            add(stringResource(R.string.map_status_no_position, snapshot.withoutPosition))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).background(
                when {
                    snapshot == null -> Color(0xFF7A8797)
                    isLive -> Color(0xFF2E9E5B)
                    else -> Color(0xFFE0A800)
                },
                CircleShape,
            ),
        )
        Spacer(Modifier.size(7.dp))
        Text(
            parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FamilyChips(
    counts: Map<ProductFamily, Int>,
    total: Int,
    selected: ProductFamily?,
    onSelect: (ProductFamily?) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Chip("${stringResource(R.string.filter_all)} $total", selected == null) { onSelect(null) }
        ProductFamily.entries.filter { (counts[it] ?: 0) > 0 }.forEach { option ->
            val name = option.displayName ?: stringResource(
                if (option == ProductFamily.COMMUTER) R.string.service_commuter else R.string.family_other,
            )
            Chip("$name ${counts[option]}", selected == option) { onSelect(option) }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

@Composable
private fun MapButton(icon: ImageVector, description: Int, onClick: () -> Unit) {
    Panel {
        IconButton(onClick = onClick) { Icon(icon, stringResource(description)) }
    }
}

@Composable
private fun Summary(stats: NetworkStats, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Figure("${stats.onMap}", stringResource(R.string.stat_on_map))
                Figure("${stats.delayed}", stringResource(R.string.stat_delayed), Color(android.graphics.Color.parseColor(delayColorHex(DelayLevel.MODERATE))))
                Figure("${stats.averageDelayMinutes} min", stringResource(R.string.stat_average))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendItem(DelayLevel.ON_TIME, R.string.legend_on_time)
                LegendItem(DelayLevel.MINOR, R.string.legend_minor)
                LegendItem(DelayLevel.MODERATE, R.string.legend_moderate)
                LegendItem(DelayLevel.SEVERE, R.string.legend_severe)
            }
        }
    }
}

@Composable
private fun Figure(value: String, name: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendItem(level: DelayLevel, label: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color(android.graphics.Color.parseColor(delayColorHex(level))), CircleShape))
        Spacer(Modifier.size(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TrainMap(
    trains: List<Train>,
    stations: List<Station>,
    incidents: List<IncidentEntity>,
    dark: Boolean,
    onReady: (MapLibreMap) -> Unit,
    onTrainClick: (String) -> Unit,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onClick by rememberUpdatedState(onTrainClick)
    val onIncident by rememberUpdatedState(onIncidentClick)
    val ready by rememberUpdatedState(onReady)
    var loaded by remember { mutableStateOf<Style?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder().target(SPAIN).zoom(SPAIN_ZOOM).build()
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.addOnMapClickListener { point ->
                    val screen: PointF = map.projection.toScreenLocation(point)
                    // Trains win over the ring around them, so a delayed train opens its own sheet.
                    val train = map.queryRenderedFeatures(screen, DOTS).firstOrNull()
                    val ring = if (train == null) map.queryRenderedFeatures(screen, RINGS).firstOrNull() else null
                    train?.getStringProperty("id")?.let { onClick(it) }
                    ring?.getStringProperty("key")?.let { onIncident(it) }
                    train != null || ring != null
                }
                map.setStyle(Style.Builder().fromUri(mapStyleUrl(dark))) { style ->
                    addRailLayers(style, stations, MapColors(dark))
                    loaded = style
                    ready(map)
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
        // A new map can arrive when the app is already started, as when the theme changes.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(loaded, trains) {
        val source = loaded?.getSourceAs<GeoJsonSource>(TRAINS) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                trains.map { train ->
                    Feature.fromGeometry(Point.fromLngLat(train.longitude, train.latitude)).apply {
                        addStringProperty("id", train.id)
                        addStringProperty("color", delayColorHex(train.delayLevel))
                        // The delay is also written, so the map can be read without telling colours apart.
                        val name = train.label ?: train.number
                        val late = train.delayMinutes?.takeIf { it >= 5 }
                        addStringProperty("label", if (late != null) "$name +$late'" else name)
                    }
                },
            ),
        )
    }

    LaunchedEffect(loaded, incidents) {
        val source = loaded?.getSourceAs<GeoJsonSource>(INCIDENTS) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                incidents.map { incident ->
                    Feature.fromGeometry(Point.fromLngLat(incident.longitude!!, incident.latitude!!)).apply {
                        addStringProperty("key", incident.key)
                        addStringProperty("color", severityColorHex(incident.severity))
                        addNumberProperty("rank", incident.severity.ordinal)
                    }
                },
            ),
        )
    }

    // A slow pulse marks the rings as live. It only runs while there is something to show.
    LaunchedEffect(loaded, incidents.isNotEmpty()) {
        val rings = loaded?.getLayerAs<CircleLayer>(RINGS) ?: return@LaunchedEffect
        if (incidents.isEmpty()) return@LaunchedEffect
        var phase = 0.0
        while (true) {
            phase = (phase + 0.07) % (2 * Math.PI)
            val wave = ((sin(phase) + 1) / 2).toFloat()
            rings.setProperties(
                PropertyFactory.circleStrokeOpacity(0.45f + 0.55f * wave),
                PropertyFactory.circleOpacity(0.10f + 0.14f * wave),
            )
            delay(PULSE_FRAME_MILLIS)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** The drawing order, bottom to top: rail network, stations, incident rings, trains, labels. */
private fun addRailLayers(style: Style, stations: List<Station>, colors: MapColors) {
    style.addSource(GeoJsonSource(NETWORK, URI("asset://catalog/network.geojson")))
    style.addSource(
        GeoJsonSource(
            STATIONS,
            FeatureCollection.fromFeatures(
                stations.map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) },
            ),
        ),
    )
    style.addSource(GeoJsonSource(INCIDENTS))
    style.addSource(GeoJsonSource(TRAINS))

    fun railLayer(id: String, highSpeed: Int, color: String, thin: Float, wide: Float) =
        LineLayer(id, NETWORK).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(4, thin), Expression.stop(12, wide),
                ),
            ),
        ).withFilter(Expression.eq(Expression.get("hs"), Expression.literal(highSpeed)))

    style.addLayer(railLayer("rail-conventional", 0, colors.conventional, 0.7f, 2f))
    style.addLayer(railLayer("rail-high-speed", 1, colors.highSpeed, 1.3f, 3f))
    style.addLayer(
        CircleLayer("station-dots", STATIONS).withProperties(
            PropertyFactory.circleColor(colors.station),
            PropertyFactory.circleRadius(2.2f),
            PropertyFactory.circleStrokeColor(colors.halo),
            PropertyFactory.circleStrokeWidth(0.8f),
        ).apply { minZoom = STATION_MIN_ZOOM },
    )
    style.addLayer(
        CircleLayer(RINGS, INCIDENTS).withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleOpacity(0.18f),
            PropertyFactory.circleStrokeColor(Expression.get("color")),
            PropertyFactory.circleStrokeWidth(2.5f),
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(4, 11f), Expression.stop(10, 18f), Expression.stop(14, 26f),
                ),
            ),
            PropertyFactory.circleSortKey(Expression.get("rank")),
        ),
    )
    style.addLayer(
        CircleLayer(DOTS, TRAINS).withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(4, 3.5f), Expression.stop(8, 6f), Expression.stop(13, 10f),
                ),
            ),
            PropertyFactory.circleStrokeWidth(1.2f),
            PropertyFactory.circleStrokeColor(colors.halo),
        ),
    )
    style.addLayer(
        SymbolLayer(LABELS, TRAINS).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textOffset(arrayOf(0f, 1.4f)),
            PropertyFactory.textColor(colors.text),
            PropertyFactory.textHaloColor(colors.halo),
            PropertyFactory.textHaloWidth(1.4f),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
        ).apply { minZoom = LABEL_MIN_ZOOM },
    )
}

@Composable
private fun TrainDetails(train: Train) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        // The answer people come for, in one line and in the colour of the map dot.
        val delay = train.delayMinutes
        Text(
            when {
                delay == null -> stringResource(R.string.train_headline_unknown)
                delay < 5 -> stringResource(R.string.train_headline_on_time)
                else -> stringResource(R.string.train_headline_late, durationText(context, delay))
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(android.graphics.Color.parseColor(delayColorHex(train.delayLevel))),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (train.label != null) stringResource(R.string.train_line_number, train.label, train.number)
            else stringResource(R.string.train_number, train.number),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(14.dp))
        val station = train.nextStation
        Detail(
            stringResource(R.string.train_now),
            when {
                station == null -> stringResource(R.string.train_on_the_way)
                train.status == TrainStatus.STOPPED -> stringResource(R.string.train_stopped_at, station)
                train.status == TrainStatus.ARRIVING -> stringResource(R.string.train_arriving_at, station)
                else -> stringResource(R.string.train_heading_to, station)
            },
        )
        if (train.origin != null && train.destination != null) {
            Detail(stringResource(R.string.train_journey), stringResource(R.string.train_from_to, train.origin, train.destination))
        }
        Detail(
            stringResource(R.string.train_network),
            train.core?.displayName ?: stringResource(R.string.service_long_distance),
        )
    }
}

@Composable
private fun Detail(name: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

/** Shown when the picture is incomplete, so nobody mistakes old data for live data. */
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
