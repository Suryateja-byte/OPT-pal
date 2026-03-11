package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.UscisCaseRefreshResult
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UscisTrackedFormType
import com.sidekick.opt_pal.data.model.UscisTrackerAvailability
import kotlinx.coroutines.flow.Flow

interface CaseStatusRepository {
    fun observeTrackedCases(uid: String): Flow<List<UscisCaseTracker>>
    suspend fun getTrackerAvailability(): Result<UscisTrackerAvailability>
    suspend fun trackCase(
        receiptNumber: String,
        expectedFormType: UscisTrackedFormType? = null
    ): Result<String>
    suspend fun refreshCase(caseId: String): Result<UscisCaseRefreshResult>
    suspend fun archiveCase(caseId: String): Result<Unit>
    suspend fun removeCase(caseId: String): Result<Unit>
    suspend fun handleMessagingTokenRefresh(token: String): Result<Unit>
    suspend fun syncMessagingEndpoint(enabled: Boolean = true, tokenOverride: String? = null): Result<Unit>
}
