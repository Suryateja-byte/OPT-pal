package com.sidekick.opt_pal.data.repository

import android.content.Context
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.sidekick.opt_pal.core.security.SecurityManager
import com.sidekick.opt_pal.data.model.UscisCaseRefreshResult
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UscisTrackerAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val PREF_INSTALLATION_ID = "uscis_tracker_installation_id"
private const val PREF_FCM_TOKEN = "uscis_tracker_fcm_token"
private const val PREF_ENDPOINT_ENABLED = "uscis_tracker_endpoint_enabled"

class CaseStatusRepositoryImpl(
    context: Context,
    private val securityManager: SecurityManager
) : CaseStatusRepository {

    private val appContext = context.applicationContext
    private val firestore = Firebase.firestore
    private val functions = Firebase.functions
    private val auth = Firebase.auth
    private val prefs = securityManager.encryptedPrefs

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
        prefs.edit().putString(PREF_FCM_TOKEN, token).apply()
        if (!prefs.getBoolean(PREF_ENDPOINT_ENABLED, false)) {
            return@runCatching
        }
        syncMessagingEndpoint(enabled = true, tokenOverride = token).getOrThrow()
    }

    override suspend fun syncMessagingEndpoint(enabled: Boolean, tokenOverride: String?): Result<Unit> = runCatching {
        val normalizedToken = tokenOverride?.trim().takeUnless { it.isNullOrBlank() }
        if (normalizedToken != null) {
            prefs.edit().putString(PREF_FCM_TOKEN, normalizedToken).apply()
        }
        prefs.edit().putBoolean(PREF_ENDPOINT_ENABLED, enabled).apply()

        val currentUser = auth.currentUser ?: return@runCatching
        val installationId = getInstallationId()
        if (!enabled) {
            functions
                .getHttpsCallable("syncUscisCaseTrackerDevice")
                .call(
                    mapOf(
                        "installationId" to installationId,
                        "token" to null,
                        "enabled" to false
                    )
                )
                .await()
            return@runCatching
        }

        val token = normalizedToken
            ?: prefs.getString(PREF_FCM_TOKEN, null)
            ?: FirebaseMessaging.getInstance().token.await().also {
                prefs.edit().putString(PREF_FCM_TOKEN, it).apply()
            }

        functions
            .getHttpsCallable("syncUscisCaseTrackerDevice")
            .call(
                mapOf(
                    "installationId" to installationId,
                    "token" to token,
                    "enabled" to enabled
                )
            )
            .await()
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
