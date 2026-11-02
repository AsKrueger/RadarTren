package com.askrueger.radartren.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.askrueger.radartren.RadarTrenApp
import com.askrueger.radartren.data.AppLanguage
import com.askrueger.radartren.data.Settings
import com.askrueger.radartren.data.ThemeMode
import com.askrueger.radartren.data.local.ReportEntity
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.report.displayText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant

data class StorageUse(val cacheBytes: Long = 0, val savedBytes: Long = 0)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as RadarTrenApp).container

    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val live = container.live.live
    val incidents = container.database.incidents().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reports = container.database.reports().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val offlineMapState = container.offlineMaps.state
    val catalog get() = container.catalog

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _storage = MutableStateFlow(StorageUse())
    val storage: StateFlow<StorageUse> = _storage

    val announcements = container.announcements

    /** Runs while the app is visible; background work takes over at its own pace otherwise. */
    suspend fun pollWhileVisible() {
        container.foreground = true
        try {
            while (true) {
                refresh()
                delay((settings.value?.refreshSeconds ?: 30) * 1000L)
            }
        } finally {
            container.foreground = false
        }
    }

    private suspend fun refresh() {
        _refreshing.value = true
        container.collect()
        _refreshing.value = false
    }

    /** Applied only on an explicit choice, so the per-app language in Android settings also holds. */
    fun setLanguage(value: AppLanguage) {
        save { container.settings.setLanguage(value) }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value.tag.orEmpty()))
    }

    fun setTheme(value: ThemeMode) = save { container.settings.setTheme(value) }
    fun setRefreshSeconds(value: Int) = save { container.settings.setRefreshSeconds(value) }
    fun setBackgroundCollection(value: Boolean) = save { container.settings.setBackgroundCollection(value) }
    fun setWifiOnly(value: Boolean) = save { container.settings.setWifiOnly(value) }
    fun setNotifyFrom(value: Severity?) = save { container.settings.setNotifyFrom(value) }
    fun acceptDisclaimer() = save { container.settings.acceptDisclaimer() }

    fun setOfflineMaps(enabled: Boolean) = viewModelScope.launch {
        container.settings.setOfflineMaps(enabled)
        if (enabled) container.offlineMaps.enable() else container.offlineMaps.disable()
        loadStorage()
    }

    fun loadStorage() = viewModelScope.launch {
        _storage.value = StorageUse(container.storage.cacheBytes(), container.storage.savedBytes())
    }

    fun clearCache() = viewModelScope.launch {
        container.storage.clearCache()
        loadStorage()
    }

    fun clearSavedData() = viewModelScope.launch {
        container.storage.clearSavedData()
        loadStorage()
    }

    /** The folder is created by [com.askrueger.radartren.data.Storage]; see res/xml/file_paths.xml. */
    fun reportUri(context: Context, report: ReportEntity): Uri =
        uri(context, File(container.storage.reportsDir, report.fileName))

    /** Writes the incident history as CSV in the language of [context] and returns its share Uri. */
    suspend fun exportIncidents(context: Context): Uri? = withContext(Dispatchers.IO) {
        val file = File(container.storage.exportsDir, "radartren-incidencias.csv")
        try {
            file.bufferedWriter().use { out ->
                out.appendLine("first_seen;last_seen;resolved_at;severity;category;source;verified;area;lines;text")
                container.database.incidents().all().forEach { i ->
                    val cells = listOf(
                        iso(i.firstSeen), iso(i.lastSeen), i.resolvedAt?.let(::iso).orEmpty(), i.severity.name, i.category.name,
                        i.source.name, i.verified.toString(), i.core?.displayName.orEmpty(), i.lines, i.displayText(context),
                    )
                    out.appendLine(cells.joinToString(";") { "\"" + it.replace("\"", "\"\"") + "\"" })
                }
            }
            uri(context, file)
        } catch (_: IOException) {
            null
        }
    }

    private fun iso(epoch: Long) = Instant.ofEpochSecond(epoch).toString()

    private fun uri(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
