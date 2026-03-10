package com.sidekick.opt_pal.core.casestatus

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

const val USCIS_CASE_UPDATES_CHANNEL_ID = "uscis_case_updates"
const val EXTRA_USCIS_CASE_ID = "extra_uscis_case_id"

class UscisCaseNotificationManager(
    private val context: Context
) {
    fun initialize() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            USCIS_CASE_UPDATES_CHANNEL_ID,
            "USCIS case updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when a tracked USCIS case changes status."
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(channel)
    }

    fun showCaseUpdatedNotification(caseId: String, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_USCIS_CASE_ID, caseId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            caseId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, USCIS_CASE_UPDATES_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(caseId.hashCode(), notification)
    }
}
