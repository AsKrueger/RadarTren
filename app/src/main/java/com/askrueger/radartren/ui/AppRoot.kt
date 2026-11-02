package com.askrueger.radartren.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.askrueger.radartren.R
import com.askrueger.radartren.data.LiveData
import com.askrueger.radartren.data.Settings
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.FeedState
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.report.displayText
import com.askrueger.radartren.ui.common.label
import com.askrueger.radartren.ui.incidents.IncidentsScreen
import com.askrueger.radartren.ui.map.DataWarning
import com.askrueger.radartren.ui.map.MapFocus
import com.askrueger.radartren.ui.map.MapScreen
import com.askrueger.radartren.ui.reports.ReportsScreen
import com.askrueger.radartren.ui.settings.SettingsScreen

private enum class Tab(val title: Int, val icon: ImageVector) {
    MAP(R.string.tab_map, Icons.Default.Map),
    INCIDENTS(R.string.tab_incidents, Icons.Default.ReportProblem),
    REPORTS(R.string.tab_reports, Icons.Default.Description),
    SETTINGS(R.string.tab_settings, Icons.Default.Settings),
}

@Composable
fun AppRoot(viewModel: AppViewModel, settings: Settings, onDisclaimerAccepted: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val live by viewModel.live.collectAsStateWithLifecycle()
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.MAP) }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    val snackbars = remember { SnackbarHostState() }

    val active = remember(incidents) { incidents.filter { it.resolvedAt == null && it.verified } }
    val notable = active.count { it.severity >= Severity.MEDIUM }
    val urgent = active.any { it.severity >= Severity.HIGH }

    fun showOnMap(incident: IncidentEntity) {
        val latitude = incident.latitude ?: return
        val longitude = incident.longitude ?: return
        focus = MapFocus(latitude, longitude, incident.key)
        tab = Tab.MAP
    }

    // New incidents while the app is open arrive here instead of as system notifications.
    LaunchedEffect(Unit) {
        viewModel.announcements.collect { news ->
            val worst = news.maxByOrNull { it.severity } ?: return@collect
            val more = news.size - 1
            val text = buildString {
                append(resources.getString(R.string.snackbar_new_incident, label(context, worst.severity), worst.displayText(context)))
                if (more > 0) append(" ").append(resources.getQuantityString(R.plurals.snackbar_more, more, more))
            }
            val result = snackbars.showSnackbar(
                message = text,
                actionLabel = resources.getString(R.string.action_view),
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (worst.latitude != null && worst.resolvedAt == null) showOnMap(worst) else tab = Tab.INCIDENTS
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            if (item == Tab.INCIDENTS && notable > 0) {
                                BadgedBox(badge = {
                                    Badge(
                                        containerColor = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                    ) { Text("$notable") }
                                }) { Icon(item.icon, null) }
                            } else {
                                Icon(item.icon, null)
                            }
                        },
                        label = { Text(stringResource(item.title)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            dataWarning(live, tab)?.let { DataWarning(stringResource(it)) }
            when (tab) {
                Tab.MAP -> MapScreen(
                    live = live,
                    stations = viewModel.catalog.allStations,
                    incidents = active,
                    focus = focus,
                    onFocusShown = { focus = null },
                    onOpenIncidents = { tab = Tab.INCIDENTS },
                    modifier = Modifier.weight(1f),
                )
                Tab.INCIDENTS -> IncidentsScreen(incidents, live, ::showOnMap, Modifier.weight(1f))
                Tab.REPORTS -> ReportsScreen(reports, { viewModel.reportUri(context, it) }, Modifier.weight(1f))
                Tab.SETTINGS -> SettingsScreen(viewModel, settings, live, Modifier.weight(1f))
            }
        }
    }

    if (!settings.disclaimerAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.disclaimer_title)) },
            text = { Text(stringResource(R.string.disclaimer_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.acceptDisclaimer()
                    onDisclaimerAccepted()
                }) { Text(stringResource(R.string.action_understood)) }
            },
        )
    }
}

/**
 * Position and delay problems outrank the alerts one and show everywhere, since they affect what
 * the map itself shows. Frozen notices only matter where notices are listed.
 */
private fun dataWarning(live: LiveData?, tab: Tab): Int? {
    val snapshot = live?.snapshot ?: return null
    return when {
        snapshot.health.any { it.state == FeedState.UNREACHABLE && it.feed != Feed.ALERTS } -> R.string.warning_offline
        !snapshot.liveDataComplete -> R.string.warning_stale
        tab == Tab.INCIDENTS && !snapshot.officialAlertsLive -> R.string.warning_alerts_frozen
        else -> null
    }
}
