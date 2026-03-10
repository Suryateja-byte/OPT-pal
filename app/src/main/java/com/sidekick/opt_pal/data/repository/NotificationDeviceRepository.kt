package com.sidekick.opt_pal.data.repository

data class NotificationDeviceChannels(
    val caseStatusEnabled: Boolean = false,
    val policyAlertsEnabled: Boolean = false
)

interface NotificationDeviceRepository {
    suspend fun handleTokenRefresh(token: String): Result<Unit>
    suspend fun syncChannels(
        caseStatusEnabled: Boolean? = null,
        policyAlertsEnabled: Boolean? = null,
        tokenOverride: String? = null
    ): Result<NotificationDeviceChannels>

    fun isCaseStatusEnabled(): Boolean
    fun isPolicyAlertsEnabled(): Boolean
}
