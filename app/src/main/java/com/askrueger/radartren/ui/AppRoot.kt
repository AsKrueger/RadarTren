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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.askrueger.radartren.R
import com.askrueger.radartren.data.LiveData
import com.askrueger.radartren.data.Settings
import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.FeedState
import com.askrueger.radartren.ui.incidents.IncidentsScreen
import com.askrueger.radartren.ui.map.DataWarning
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
    val live by viewModel.live.collectAsStateWithLifecycle()
    val incidents by viewModel.incidents.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.MAP) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, null) },
                        label = { Text(stringResource(item.title)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            dataWarning(live)?.let { DataWarning(stringResource(it)) }
            when (tab) {
                Tab.MAP -> MapScreen(live, Modifier.weight(1f))
                Tab.INCIDENTS -> IncidentsScreen(incidents, Modifier.weight(1f))
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

/** Position and delay problems outrank the alerts one: they affect what the map itself shows. */
private fun dataWarning(live: LiveData?): Int? {
    val snapshot = live?.snapshot ?: return null
    return when {
        snapshot.health.any { it.state == FeedState.UNREACHABLE && it.feed != Feed.ALERTS } -> R.string.warning_offline
        !snapshot.liveDataComplete -> R.string.warning_stale
        !snapshot.officialAlertsLive -> R.string.warning_alerts_frozen
        else -> null
    }
}
