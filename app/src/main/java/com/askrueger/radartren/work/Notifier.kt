package com.askrueger.radartren.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.askrueger.radartren.MainActivity
import com.askrueger.radartren.R
import com.askrueger.radartren.data.local.IncidentEntity
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.Severity
import com.askrueger.radartren.report.displayText
import com.askrueger.radartren.ui.common.label

/** One channel per urgency, so people can tune each level from Android settings too. */
object Notifier {
    private fun channel(severity: Severity) = when {
        severity >= Severity.CRITICAL -> "critical"
        severity == Severity.HIGH -> "high"
        else -> "normal"
    }

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel("critical", label(context, Severity.CRITICAL), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel("high", label(context, Severity.HIGH), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(
                    "normal", context.getString(R.string.channel_other), NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }

    fun notify(context: Context, incidents: List<IncidentEntity>) {
        if (incidents.isEmpty()) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val manager = NotificationManagerCompat.from(context)
        incidents.forEach { incident ->
            val source = context.getString(
                if (incident.source == IncidentSource.OFFICIAL) R.string.source_official else R.string.source_detected,
            )
            val area = incident.core?.displayName ?: context.getString(R.string.service_long_distance)
            val body = incident.displayText(context)
            val notification = NotificationCompat.Builder(context, channel(incident.severity))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${label(context, incident.severity)} · $area")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body).setSummaryText(source))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(incident.key.hashCode(), notification)
        }
    }
}
