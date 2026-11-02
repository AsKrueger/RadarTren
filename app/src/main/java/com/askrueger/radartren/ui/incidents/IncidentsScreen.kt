package com.askrueger.radartren.ui.incidents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.askrueger.radartren.R
import com.askrueger.radartren.data.LiveData
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.report.displayText
import com.askrueger.radartren.ui.common.categoryLabel
import com.askrueger.radartren.ui.common.clock
import com.askrueger.radartren.ui.common.label
import com.askrueger.radartren.ui.common.severityColor

private enum class IncidentFilter { ACTIVE, URGENT, RESOLVED, UNVERIFIED }

@Composable
fun IncidentsScreen(
    incidents: List<IncidentEntity>,
    live: LiveData?,
    onShowOnMap: (IncidentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(IncidentFilter.ACTIVE) }
    val shown = remember(incidents, filter) {
        incidents.filter {
            val active = it.resolvedAt == null
            when (filter) {
                IncidentFilter.ACTIVE -> active && it.verified
                IncidentFilter.URGENT -> active && it.verified && it.severity >= Severity.HIGH
                IncidentFilter.RESOLVED -> !active && it.verified
                IncidentFilter.UNVERIFIED -> !it.verified
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        LiveHeader(live)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IncidentFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    IncidentFilter.ACTIVE -> R.string.incidents_active
                                    IncidentFilter.URGENT -> R.string.incidents_urgent
                                    IncidentFilter.RESOLVED -> R.string.incidents_resolved
                                    IncidentFilter.UNVERIFIED -> R.string.incidents_unverified
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (filter == IncidentFilter.UNVERIFIED) {
            Text(
                stringResource(R.string.incidents_unverified_note),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.incidents_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = IncidentEntity::key) { incident ->
                    IncidentCard(incident, onShowOnMap.takeIf { incident.hasLocation && incident.resolvedAt == null })
                }
            }
        }
    }
}

/** States plainly how current the list is: it is rebuilt on every refresh while the app is open. */
@Composable
private fun LiveHeader(live: LiveData?) {
    val isLive = live?.snapshot?.liveDataComplete == true
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).background(if (isLive) Color(0xFF2E9E5B) else Color(0xFFE0A800), CircleShape),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            when {
                live == null -> stringResource(R.string.map_status_loading)
                isLive -> stringResource(R.string.incidents_live, clock(live.fetchedAt, seconds = true))
                else -> stringResource(R.string.incidents_not_live, clock(live.fetchedAt, seconds = true))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IncidentCard(incident: IncidentEntity, onShowOnMap: ((IncidentEntity) -> Unit)?) {
    val context = LocalContext.current
    val clickable = if (onShowOnMap != null) Modifier.clickable { onShowOnMap(incident) } else Modifier
    Card(Modifier.fillMaxWidth().then(clickable)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(severityColor(incident.severity), CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(
                    "${label(context, incident.severity)} · ${categoryLabel(context, incident.category)}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (onShowOnMap != null) {
                    Icon(
                        Icons.Default.Place,
                        stringResource(R.string.incident_show_on_map),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(incident.displayText(context), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            val area = incident.core?.displayName ?: stringResource(R.string.service_long_distance)
            val source = stringResource(
                if (incident.source == IncidentSource.OFFICIAL) R.string.source_official else R.string.source_detected,
            )
            val time = when (val resolved = incident.resolvedAt) {
                null -> stringResource(R.string.incident_since, clock(incident.firstSeen))
                else -> stringResource(R.string.incident_period, clock(incident.firstSeen), clock(resolved))
            }
            Text(
                "$area · $source · $time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
