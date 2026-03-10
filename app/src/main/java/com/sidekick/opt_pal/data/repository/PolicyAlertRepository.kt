package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.PolicyAlertAvailability
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertState
import kotlinx.coroutines.flow.Flow

interface PolicyAlertRepository {
    suspend fun resolveAvailability(): Result<PolicyAlertAvailability>
    fun observePublishedAlerts(): Flow<List<PolicyAlertCard>>
    fun observeAlertStates(uid: String): Flow<List<PolicyAlertState>>
    suspend fun markAlertOpened(uid: String, alertId: String): Result<Unit>
    suspend fun syncNotificationPreference(enabled: Boolean = true, tokenOverride: String? = null): Result<Unit>
    fun isNotificationPreferenceEnabled(): Boolean
}
