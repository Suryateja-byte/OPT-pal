package com.sidekick.opt_pal.data.repository

import android.content.Context
import com.google.firebase.auth.ktx.auth
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.sidekick.opt_pal.core.security.SecurityManager
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val PREF_INSTALLATION_ID = "notification_installation_id"
private const val PREF_FCM_TOKEN = "notification_fcm_token"
private const val PREF_CASE_STATUS_ENABLED = "notification_case_status_enabled"
private const val PREF_POLICY_ALERTS_ENABLED = "notification_policy_alerts_enabled"

class NotificationDeviceRepositoryImpl(
    context: Context,
    private val securityManager: SecurityManager
) : NotificationDeviceRepository {

    private val appContext = context.applicationContext
    private val auth = Firebase.auth
    private val functions = Firebase.functions
    private val prefs = securityManager.encryptedPrefs

    override suspend fun handleTokenRefresh(token: String): Result<Unit> = runCatching {
        prefs.edit().putString(PREF_FCM_TOKEN, token).apply()
        if (!isCaseStatusEnabled() && !isPolicyAlertsEnabled()) {
            return@runCatching
        }
        syncChannels(tokenOverride = token).getOrThrow()
    }

    override suspend fun syncChannels(
        caseStatusEnabled: Boolean?,
        policyAlertsEnabled: Boolean?,
        tokenOverride: String?
    ): Result<NotificationDeviceChannels> = runCatching {
        caseStatusEnabled?.let { prefs.edit().putBoolean(PREF_CASE_STATUS_ENABLED, it).apply() }
        policyAlertsEnabled?.let { prefs.edit().putBoolean(PREF_POLICY_ALERTS_ENABLED, it).apply() }
        tokenOverride?.trim()?.takeIf { it.isNotBlank() }?.let {
            prefs.edit().putString(PREF_FCM_TOKEN, it).apply()
        }

        val resolvedChannels = NotificationDeviceChannels(
            caseStatusEnabled = isCaseStatusEnabled(),
            policyAlertsEnabled = isPolicyAlertsEnabled()
        )
        val currentUser = auth.currentUser ?: return@runCatching resolvedChannels
        val installationId = getInstallationId()
        val token = tokenOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: prefs.getString(PREF_FCM_TOKEN, null)
            ?: FirebaseMessaging.getInstance().token.await().also {
                prefs.edit().putString(PREF_FCM_TOKEN, it).apply()
            }

        functions.getHttpsCallable("syncNotificationDevice")
            .call(
                mapOf(
                    "installationId" to installationId,
                    "token" to if (resolvedChannels.caseStatusEnabled || resolvedChannels.policyAlertsEnabled) token else null,
                    "caseStatusEnabled" to resolvedChannels.caseStatusEnabled,
                    "policyAlertsEnabled" to resolvedChannels.policyAlertsEnabled
                )
            )
            .await()
        resolvedChannels
    }

    override fun isCaseStatusEnabled(): Boolean {
        return prefs.getBoolean(PREF_CASE_STATUS_ENABLED, false)
    }

    override fun isPolicyAlertsEnabled(): Boolean {
        return prefs.getBoolean(PREF_POLICY_ALERTS_ENABLED, false)
    }

    private fun getInstallationId(): String {
        val existing = prefs.getString(PREF_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val generated = "${appContext.packageName}-${UUID.randomUUID()}"
        prefs.edit().putString(PREF_INSTALLATION_ID, generated).apply()
        return generated
    }
}
