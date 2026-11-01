package com.askrueger.radartren.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.askrueger.radartren.domain.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AppLanguage(val tag: String?) { SYSTEM(null), SPANISH("es"), ENGLISH("en") }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val refreshSeconds: Int = 30,
    val offlineMaps: Boolean = false,
    val backgroundCollection: Boolean = true,
    val wifiOnly: Boolean = false,
    /** Lowest severity that raises a notification; null turns notifications off. */
    val notifyFrom: Severity? = Severity.HIGH,
    val disclaimerAccepted: Boolean = false,
)

class SettingsRepository(context: Context) {
    private val store = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("settings")
    }

    val settings: Flow<Settings> = store.data.map(::read)

    suspend fun current(): Settings = settings.first()

    suspend fun setLanguage(value: AppLanguage) = store.edit { it[LANGUAGE] = value.name }
    suspend fun setTheme(value: ThemeMode) = store.edit { it[THEME] = value.name }
    suspend fun setRefreshSeconds(value: Int) = store.edit { it[REFRESH] = value }
    suspend fun setOfflineMaps(value: Boolean) = store.edit { it[OFFLINE_MAPS] = value }
    suspend fun setBackgroundCollection(value: Boolean) = store.edit { it[BACKGROUND] = value }
    suspend fun setWifiOnly(value: Boolean) = store.edit { it[WIFI_ONLY] = value }
    suspend fun setNotifyFrom(value: Severity?) = store.edit { it[NOTIFY_FROM] = value?.name ?: OFF }
    suspend fun acceptDisclaimer() = store.edit { it[DISCLAIMER] = true }

    private fun read(p: Preferences) = Settings(
        language = enumOf(p[LANGUAGE], AppLanguage.SYSTEM),
        theme = enumOf(p[THEME], ThemeMode.SYSTEM),
        refreshSeconds = p[REFRESH] ?: 30,
        offlineMaps = p[OFFLINE_MAPS] ?: false,
        backgroundCollection = p[BACKGROUND] ?: true,
        wifiOnly = p[WIFI_ONLY] ?: false,
        notifyFrom = when (val stored = p[NOTIFY_FROM]) {
            null -> Severity.HIGH
            OFF -> null
            else -> enumOf(stored, Severity.HIGH)
        },
        disclaimerAccepted = p[DISCLAIMER] ?: false,
    )

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val OFF = "OFF"
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val REFRESH = intPreferencesKey("refresh_seconds")
        val OFFLINE_MAPS = booleanPreferencesKey("offline_maps")
        val BACKGROUND = booleanPreferencesKey("background_collection")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val NOTIFY_FROM = stringPreferencesKey("notify_from")
        val DISCLAIMER = booleanPreferencesKey("disclaimer_accepted")
    }
}
