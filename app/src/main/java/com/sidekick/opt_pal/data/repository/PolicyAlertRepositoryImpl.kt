package com.sidekick.opt_pal.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.sidekick.opt_pal.data.model.PolicyAlertAvailability
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PolicyAlertRepositoryImpl(
    private val notificationDeviceRepository: NotificationDeviceRepository
) : PolicyAlertRepository {

    private val firestore = Firebase.firestore
    private val remoteConfig = Firebase.remoteConfig

    init {
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 60L * 60L
            }
        )
        remoteConfig.setDefaultsAsync(
            mapOf(
                REMOTE_CONFIG_POLICY_ALERTS_ENABLED to true
            )
        )
    }

    override suspend fun resolveAvailability(): Result<PolicyAlertAvailability> = runCatching {
        runCatching { remoteConfig.fetchAndActivate().await() }
        if (remoteConfig.getBoolean(REMOTE_CONFIG_POLICY_ALERTS_ENABLED)) {
            PolicyAlertAvailability(
                isEnabled = true,
                message = "Policy Alert Feed is enabled."
            )
        } else {
            PolicyAlertAvailability(
                isEnabled = false,
                message = "Policy Alert Feed is still in a limited rollout."
            )
        }
    }

    override fun observePublishedAlerts(): Flow<List<PolicyAlertCard>> {
        return firestore.collection("policyAlerts")
            .orderBy("publishedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<PolicyAlertCard>() }
    }

    override fun observeAlertStates(uid: String): Flow<List<PolicyAlertState>> {
        return firestore.collection("users")
            .document(uid)
            .collection("policyAlertState")
            .snapshots()
            .map { snapshot -> snapshot.toObjects<PolicyAlertState>() }
    }

    override suspend fun markAlertOpened(uid: String, alertId: String): Result<Unit> = runCatching {
        val now = System.currentTimeMillis()
        firestore.collection("users")
            .document(uid)
            .collection("policyAlertState")
            .document(alertId)
            .set(
                mapOf(
                    "alertId" to alertId,
                    "openedAt" to now,
                    "lastSeenAt" to now
                )
            )
            .await()
    }

    override suspend fun syncNotificationPreference(enabled: Boolean, tokenOverride: String?): Result<Unit> {
        return notificationDeviceRepository.syncChannels(
            policyAlertsEnabled = enabled,
            tokenOverride = tokenOverride
        ).map { }
    }

    override fun isNotificationPreferenceEnabled(): Boolean {
        return notificationDeviceRepository.isPolicyAlertsEnabled()
    }

    private companion object {
        const val REMOTE_CONFIG_POLICY_ALERTS_ENABLED = "policy_alert_feed_enabled"
    }
}
