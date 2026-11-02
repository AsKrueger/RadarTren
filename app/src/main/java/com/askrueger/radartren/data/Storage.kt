package com.askrueger.radartren.data

import android.content.Context
import com.askrueger.radartren.data.local.RadarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Backs the two delete buttons in Settings and the size shown next to them. */
class Storage(
    private val context: Context,
    private val database: RadarDatabase,
    private val offlineMaps: OfflineMaps,
) {
    val reportsDir: File get() = File(context.filesDir, "reports").apply { mkdirs() }
    val exportsDir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) { context.cacheDir.size() }

    suspend fun savedBytes(): Long = withContext(Dispatchers.IO) {
        reportsDir.size() + context.getDatabasePath("radartren.db").parentFile.size()
    }

    /** Removes what can be downloaded again: temporary files and map tiles seen while browsing. */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        offlineMaps.clearViewedTiles()
    }

    /** Removes what cannot: incident history, samples and PDF reports. Settings stay. */
    suspend fun clearSavedData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        reportsDir.listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }

    private fun File?.size(): Long = this?.walkBottomUp()?.filter(File::isFile)?.sumOf(File::length) ?: 0
}
