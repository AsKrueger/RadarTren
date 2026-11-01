package com.askrueger.radartren.ui.incidents

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.askrueger.radartren.R
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.report.displayText
import com.askrueger.radartren.ui.common.categoryLabel
import com.askrueger.radartren.ui.common.label
import com.askrueger.radartren.ui.common.severityColor

private enum class IncidentFilter { ACTIVE, URGENT, RESOLVED, UNVERIFIED }

@Composable
fun IncidentsScreen(incidents: List<IncidentEntity>, modifier: Modifier = Modifier) {
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
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
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
                items(shown, key = IncidentEntity::key) { IncidentCard(it) }
            }
        }
    }
}

@Composable
private fun IncidentCard(incident: IncidentEntity) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(severityColor(incident.severity), CircleShape))
                Spacer(Modifier.size(8.dp))
                Text(
                    "${label(context, incident.severity)} · ${categoryLabel(context, incident.category)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(incident.displayText(context), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            val area = incident.core?.displayName ?: stringResource(R.string.service_long_distance)
            val source = stringResource(
                if (incident.source == IncidentSource.OFFICIAL) R.string.source_official else R.string.source_detected,
            )
            val seen = DateUtils.getRelativeTimeSpanString(incident.lastSeen * 1000)
            Text(
                "$area · $source · $seen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
