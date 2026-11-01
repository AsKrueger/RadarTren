package com.askrueger.radartren.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.askrueger.radartren.RadarTrenApp
import com.askrueger.radartren.data.Settings
import com.askrueger.radartren.domain.SAMPLE_INTERVAL_SECONDS
import com.askrueger.radartren.ui.common.localized
import java.util.concurrent.TimeUnit

/**
 * The background collection: every 15 minutes (Android's minimum) it stores a sample, updates the
 * incidents, notifies what is new and writes the reports of any period that has just closed.
 */
class CollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RadarTrenApp).container
        if (!container.collect().snapshot.liveDataComplete) return Result.retry()
        val settings = container.settings.current()
        container.reports.generateClosedPeriods(localized(applicationContext, settings.language), System.currentTimeMillis() / 1000)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "collect"

        /** Applies the current settings, replacing any previous schedule. */
        fun schedule(context: Context, settings: Settings) {
            val manager = WorkManager.getInstance(context)
            if (!settings.backgroundCollection) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val network = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request = PeriodicWorkRequestBuilder<CollectWorker>(SAMPLE_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
                .build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
