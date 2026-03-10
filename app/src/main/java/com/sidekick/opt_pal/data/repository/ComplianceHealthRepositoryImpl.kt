package com.sidekick.opt_pal.data.repository

import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.core.compliance.ComplianceScoreSnapshotStore
import com.sidekick.opt_pal.data.model.ComplianceHealthAvailability
import com.sidekick.opt_pal.data.model.ComplianceScoreSnapshotState
import kotlinx.coroutines.tasks.await

class ComplianceHealthRepositoryImpl(
    private val snapshotStore: ComplianceScoreSnapshotStore
) : ComplianceHealthRepository {

    private val remoteConfig = Firebase.remoteConfig

    init {
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 60L * 60L
            }
        )
        remoteConfig.setDefaultsAsync(
            mapOf(
                REMOTE_CONFIG_COMPLIANCE_SCORE_ENABLED to true
            )
        )
    }

    override suspend fun resolveAvailability(): Result<ComplianceHealthAvailability> = runCatching {
        runCatching { remoteConfig.fetchAndActivate().await() }
        if (remoteConfig.getBoolean(REMOTE_CONFIG_COMPLIANCE_SCORE_ENABLED)) {
            ComplianceHealthAvailability(
                isEnabled = true,
                message = "Compliance Health Score is enabled."
            )
        } else {
            ComplianceHealthAvailability(
                isEnabled = false,
                message = "Compliance Health Score is still in a limited rollout."
            )
        }
    }

    override fun syncSnapshot(uid: String, score: Int, computedAt: Long): ComplianceScoreSnapshotState {
        return snapshotStore.sync(uid = uid, score = score, computedAt = computedAt)
    }

    private companion object {
        const val REMOTE_CONFIG_COMPLIANCE_SCORE_ENABLED = "compliance_health_score_enabled"
    }
}
