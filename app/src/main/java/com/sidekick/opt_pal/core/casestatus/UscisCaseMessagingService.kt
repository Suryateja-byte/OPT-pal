package com.sidekick.opt_pal.core.casestatus

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val USCIS_CASE_UPDATE_TYPE = "uscis_case_update"
private const val POLICY_ALERT_PUBLISHED_TYPE = "policy_alert_published"

class UscisCaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            AppModule.notificationDeviceRepository.handleTokenRefresh(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        when (data["type"]) {
            USCIS_CASE_UPDATE_TYPE -> {
                val caseId = data["caseId"].orEmpty()
                if (caseId.isBlank()) return
                AppModule.uscisCaseNotificationManager.showCaseUpdatedNotification(
                    caseId = caseId,
                    title = data["title"].orEmpty().ifBlank { "USCIS case update" },
                    body = data["body"].orEmpty().ifBlank { "Your USCIS case status changed." }
                )
            }
            POLICY_ALERT_PUBLISHED_TYPE -> {
                val alertId = data["alertId"].orEmpty()
                if (alertId.isBlank()) return
                AppModule.policyAlertNotificationManager.showPolicyAlertNotification(
                    alertId = alertId,
                    title = data["title"].orEmpty().ifBlank { "Policy alert" },
                    body = data["body"].orEmpty().ifBlank { "A reviewed OPT policy alert was published." }
                )
            }
        }
    }
}
