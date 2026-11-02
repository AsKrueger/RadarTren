package com.askrueger.radartren

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.data.local.RadarDatabase
import com.askrueger.radartren.domain.Core
import com.askrueger.radartren.domain.IncidentCategory
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.work.Notifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealtimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Builds a database exactly as version 0.1.0 left it, then opens it with the current schema. */
    @Test
    fun databaseFromTheFirstVersionMigratesAndKeepsItsHistory() = runBlocking<Unit> {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            V1_SCHEMA.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO incident VALUES ('train:COMMUTER:1', 'DETECTED', 3, 'DELAY', '10', 'C1', 'C1 1: +70 min', 1, 1, 2, NULL, 1)",
            )
            db.version = 1
        }

        val database = Room.databaseBuilder(context, RadarDatabase::class.java, name).build()
        val kept = database.incidents().find("train:COMMUTER:1")!!
        assertEquals(Severity.HIGH, kept.severity)
        assertNull(kept.latitude)
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun aBurstOfIncidentsBecomesThreeNotificationsAndASummary() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        Notifier.createChannels(context)

        val burst = List(7) { index ->
            IncidentEntity(
                key = "test:$index", source = IncidentSource.DETECTED, severity = Severity.HIGH,
                category = IncidentCategory.DELAY, coreCode = Core.MADRID.code, lines = "", text = "C1 $index: +130 min",
                startedAt = 0, firstSeen = 0, lastSeen = 0, resolvedAt = null, verified = true,
            )
        }
        assertEquals(Notifier.MAX_INDIVIDUAL + 1, Notifier.notify(context, burst))
        Thread.sleep(500)
        assertEquals(Notifier.MAX_INDIVIDUAL + 1, manager.activeNotifications.size)
        manager.cancelAll()
    }

    @Test
    fun inForegroundNewIncidentsGoToTheAppInsteadOfTheSystem() = runBlocking<Unit> {
        val container = (context as RadarTrenApp).container
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        container.foreground = true
        try {
            container.collect()
            Thread.sleep(500)
            assertEquals(0, manager.activeNotifications.size)
        } finally {
            container.foreground = false
            container.storage.clearSavedData()
        }
    }

    private companion object {
        /** Copied from app/schemas/.../1.json. */
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `incident` (`key` TEXT NOT NULL, `source` TEXT NOT NULL, `severity` INTEGER NOT NULL, `category` TEXT NOT NULL, `coreCode` TEXT, `lines` TEXT NOT NULL, `text` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL, `resolvedAt` INTEGER, `verified` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `metric_sample` (`at` INTEGER NOT NULL, `trains` INTEGER NOT NULL, `withDelayInfo` INTEGER NOT NULL, `delayed5` INTEGER NOT NULL, `delayed15` INTEGER NOT NULL, `delaySumMinutes` INTEGER NOT NULL, `maxDelayMinutes` INTEGER NOT NULL, `cancelled` INTEGER NOT NULL, PRIMARY KEY(`at`))",
            "CREATE TABLE IF NOT EXISTS `report` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `startEpoch` INTEGER NOT NULL, `generatedAt` INTEGER NOT NULL, `fileName` TEXT NOT NULL, `coverage` REAL NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_incident_resolvedAt` ON `incident` (`resolvedAt`)",
            "CREATE INDEX IF NOT EXISTS `index_incident_firstSeen` ON `incident` (`firstSeen`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f343d7d9a887e3ac9a901f1e6a55f985')",
        )
    }
}
