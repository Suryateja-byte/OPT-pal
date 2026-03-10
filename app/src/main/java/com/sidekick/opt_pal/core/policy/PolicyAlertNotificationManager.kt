package com.sidekick.opt_pal.core.policy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sidekick.opt_pal.MainActivity
import com.sidekick.opt_pal.R

const val POLICY_ALERTS_CHANNEL_ID = "policy_alerts"
const val EXTRA_POLICY_ALERT_ID = "extra_policy_alert_id"

class PolicyAlertNotificationManager(
    private val context: Context
) {
    fun initialize() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            POLICY_ALERTS_CHANNEL_ID,
            "Policy alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when OPT policy alerts are published."
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(channel)
    }

    fun showPolicyAlertNotification(alertId: String, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_POLICY_ALERT_ID, alertId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alertId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, POLICY_ALERTS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(alertId.hashCode(), notification)
    }
}
