package com.askrueger.radartren.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.askrueger.radartren.BuildConfig
import com.askrueger.radartren.R
import com.askrueger.radartren.data.AppLanguage
import com.askrueger.radartren.data.LiveData
import com.askrueger.radartren.data.OfflineMapState
import com.askrueger.radartren.data.Settings
import com.askrueger.radartren.data.ThemeMode
import com.askrueger.radartren.domain.Feed
import com.askrueger.radartren.domain.FeedHealth
import com.askrueger.radartren.domain.FeedState
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.ui.AppViewModel
import com.askrueger.radartren.ui.common.feedLabel
import com.askrueger.radartren.ui.common.label
import com.askrueger.radartren.ui.reports.share
import kotlinx.coroutines.launch
import androidx.core.net.toUri

private const val PRIVACY_URL = "https://github.com/AsKrueger/RadarTren/blob/main/docs/privacidad.md"

@Composable
fun SettingsScreen(viewModel: AppViewModel, settings: Settings, live: LiveData?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val offlineState by viewModel.offlineMapState.collectAsStateWithLifecycle()
    var confirmation by remember { mutableStateOf<(Int)?>(null) }

    LaunchedEffect(Unit) { viewModel.loadStorage() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Section(R.string.settings_general)
        Choice(
            stringResource(R.string.settings_language),
            AppLanguage.entries,
            settings.language,
            {
                stringResource(
                    when (it) {
                        AppLanguage.SYSTEM -> R.string.option_system
                        AppLanguage.SPANISH -> R.string.language_spanish
                        AppLanguage.ENGLISH -> R.string.language_english
                    },
                )
            },
            viewModel::setLanguage,
        )
        Choice(
            stringResource(R.string.settings_theme),
            ThemeMode.entries,
            settings.theme,
            {
                stringResource(
                    when (it) {
                        ThemeMode.SYSTEM -> R.string.option_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    },
                )
            },
            viewModel::setTheme,
        )
        Choice(
            stringResource(R.string.settings_refresh),
            listOf(15, 30, 60),
            settings.refreshSeconds,
            { stringResource(R.string.seconds_value, it) },
            viewModel::setRefreshSeconds,
        )

        Section(R.string.settings_alerts)
        Choice(
            stringResource(R.string.settings_notify_from),
            listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, null),
            settings.notifyFrom,
            { if (it == null) stringResource(R.string.option_off) else label(context, it) },
            viewModel::setNotifyFrom,
        )
        Toggle(
            R.string.settings_background, R.string.settings_background_hint,
            settings.backgroundCollection, viewModel::setBackgroundCollection,
        )
        Toggle(R.string.settings_wifi_only, R.string.settings_wifi_only_hint, settings.wifiOnly, viewModel::setWifiOnly)

        Section(R.string.settings_offline)
        Toggle(R.string.settings_offline_maps, R.string.settings_offline_maps_hint, settings.offlineMaps, viewModel::setOfflineMaps)
        when (val state = offlineState) {
            is OfflineMapState.Downloading -> Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Hint(stringResource(R.string.offline_downloading, state.percent))
            }
            OfflineMapState.Ready -> Hint(stringResource(R.string.offline_ready), Modifier.padding(horizontal = 16.dp))
            OfflineMapState.Failed -> Hint(stringResource(R.string.offline_failed), Modifier.padding(horizontal = 16.dp))
            OfflineMapState.Idle -> Unit
        }

        Section(R.string.settings_data)
        Action(
            stringResource(R.string.settings_clear_cache),
            Formatter.formatShortFileSize(context, storage.cacheBytes),
        ) { confirmation = R.string.confirm_clear_cache }
        Action(
            stringResource(R.string.settings_clear_saved),
            Formatter.formatShortFileSize(context, storage.savedBytes),
        ) { confirmation = R.string.confirm_clear_saved }
        Action(stringResource(R.string.settings_export), stringResource(R.string.settings_export_hint)) {
            scope.launch {
                viewModel.exportIncidents(context)?.let { share(context, it, "text/csv") }
            }
        }

        Section(R.string.settings_sources)
        Feed.entries.forEach { feed -> FeedRow(feed, live?.snapshot?.healthOf(feed)) }

        Section(R.string.settings_about)
        if (BuildConfig.FEEDBACK_EMAIL.isNotBlank()) {
            Action(stringResource(R.string.settings_feedback), stringResource(R.string.settings_feedback_hint)) {
                sendFeedback(context)
            }
        }
        Action(stringResource(R.string.settings_privacy), null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri()))
        }
        Hint(stringResource(R.string.settings_disclaimer), Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Hint(stringResource(R.string.settings_attribution), Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Hint(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }

    confirmation?.let { message ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (message == R.string.confirm_clear_cache) viewModel.clearCache() else viewModel.clearSavedData()
                        confirmation = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun sendFeedback(context: Context) {
    val subject = "RadarTren ${BuildConfig.VERSION_NAME}"
    val body = "\n\n---\nAndroid ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
    val uri = "mailto:${BuildConfig.FEEDBACK_EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.feedback_no_app, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun FeedRow(feed: Feed, health: FeedHealth?) {
    val context = LocalContext.current
    val state = stringResource(
        when (health?.state) {
            FeedState.LIVE -> R.string.feed_live
            FeedState.FROZEN -> R.string.feed_frozen
            FeedState.UNREACHABLE -> R.string.feed_unreachable
            null -> R.string.feed_pending
        },
    )
    val updated = health?.updatedAt?.let {
        android.text.format.DateUtils.getRelativeTimeSpanString(it * 1000).toString()
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text(feedLabel(context, feed), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(listOfNotNull(state, updated).joinToString(" · "), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Section(title: Int) {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Text(
        stringResource(title),
        Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Toggle(title: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title))
            Hint(stringResource(hint))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Action(title: String, value: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title)
        value?.let { Hint(it) }
    }
}

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, name: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Action(title, name(selected)) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onSelect(option)
                                open = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(12.dp))
                            Text(name(option))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
