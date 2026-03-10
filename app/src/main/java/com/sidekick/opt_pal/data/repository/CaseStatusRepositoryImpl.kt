package com.sidekick.opt_pal.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.data.model.UscisCaseRefreshResult
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UscisTrackerAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CaseStatusRepositoryImpl(
    private val notificationDeviceRepository: NotificationDeviceRepository
) : CaseStatusRepository {

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions

    override fun observeTrackedCases(uid: String): Flow<List<UscisCaseTracker>> {
        return firestore.collection("users")
            .document(uid)
            .collection("uscisCases")
            .orderBy("lastChangedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<UscisCaseTracker>() }
    }

    override suspend fun getTrackerAvailability(): Result<UscisTrackerAvailability> = runCatching {
        val response = functions
            .getHttpsCallable("getUscisTrackerAvailability")
            .call()
            .await()
        val data = response.data as? Map<*, *> ?: error("Invalid USCIS tracker availability response.")
        UscisTrackerAvailability(
            mode = data["mode"] as? String ?: "",
            reason = data["reason"] as? String ?: "",
            maxTrackedCases = (data["maxTrackedCases"] as? Number)?.toInt() ?: 3
        )
    }

    override suspend fun trackCase(receiptNumber: String): Result<String> = runCatching {
        val response = functions
            .getHttpsCallable("trackUscisCase")
            .call(mapOf("receiptNumber" to receiptNumber))
            .await()
        val data = response.data as? Map<*, *> ?: error("Invalid USCIS case tracking response.")
        data["caseId"] as? String ?: error("Missing caseId.")
    }

    override suspend fun refreshCase(caseId: String): Result<UscisCaseRefreshResult> = runCatching {
        val response = functions
            .getHttpsCallable("refreshUscisCase")
            .call(mapOf("caseId" to caseId))
            .await()
        val data = response.data as? Map<*, *> ?: error("Invalid USCIS refresh response.")
        val trackerMap = data["tracker"] as? Map<*, *>
        UscisCaseRefreshResult(
            refreshed = data["refreshed"] as? Boolean ?: false,
            statusChanged = data["statusChanged"] as? Boolean ?: false,
            cooldownRemainingMinutes = (data["cooldownRemainingMinutes"] as? Number)?.toInt() ?: 0,
            caseId = data["caseId"] as? String ?: caseId,
            tracker = trackerMap?.toTracker()
        )
    }

    override suspend fun archiveCase(caseId: String): Result<Unit> = runCatching {
        functions
            .getHttpsCallable("archiveUscisCase")
            .call(mapOf("caseId" to caseId))
            .await()
    }

    override suspend fun removeCase(caseId: String): Result<Unit> = runCatching {
        functions
            .getHttpsCallable("removeUscisCase")
            .call(mapOf("caseId" to caseId))
            .await()
    }

    override suspend fun handleMessagingTokenRefresh(token: String): Result<Unit> = runCatching {
        notificationDeviceRepository.handleTokenRefresh(token).getOrThrow()
    }

    override suspend fun syncMessagingEndpoint(enabled: Boolean, tokenOverride: String?): Result<Unit> = runCatching {
        notificationDeviceRepository.syncChannels(
            caseStatusEnabled = enabled,
            tokenOverride = tokenOverride
        ).getOrThrow()
    }
}

private fun Map<*, *>.toTracker(): UscisCaseTracker {
    return UscisCaseTracker(
        id = this["receiptNumber"] as? String ?: "",
        receiptNumber = this["receiptNumber"] as? String ?: "",
        formType = this["formType"] as? String ?: "",
        normalizedStage = this["normalizedStage"] as? String ?: "",
        officialStatusText = this["officialStatusText"] as? String ?: "",
        officialStatusDescription = this["officialStatusDescription"] as? String ?: "",
        plainEnglishSummary = this["plainEnglishSummary"] as? String ?: "",
        recommendedAction = this["recommendedAction"] as? String ?: "",
        classification = this["classification"] as? String ?: "",
        confidence = this["confidence"] as? String ?: "",
        watchFor = (this["watchFor"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        statusHash = this["statusHash"] as? String ?: "",
        lastCheckedAt = (this["lastCheckedAt"] as? Number)?.toLong() ?: 0L,
        lastChangedAt = (this["lastChangedAt"] as? Number)?.toLong() ?: 0L,
        nextPollAt = (this["nextPollAt"] as? Number)?.toLong() ?: 0L,
        lastError = this["lastError"] as? String ?: "",
        consecutiveFailureCount = (this["consecutiveFailureCount"] as? Number)?.toInt() ?: 0,
        isTerminal = this["isTerminal"] as? Boolean ?: false,
        isArchived = this["isArchived"] as? Boolean ?: false,
        officialHistory = (this["officialHistory"] as? List<*>)?.mapNotNull { raw ->
            val item = raw as? Map<*, *> ?: return@mapNotNull null
            com.sidekick.opt_pal.data.model.UscisCaseHistoryEntry(
                statusText = item["statusText"] as? String ?: "",
                statusDescription = item["statusDescription"] as? String ?: "",
                statusDate = (item["statusDate"] as? Number)?.toLong()
            )
        }.orEmpty()
    )
}
