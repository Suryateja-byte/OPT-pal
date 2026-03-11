package com.sidekick.opt_pal.data.repository

import android.content.Context
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bEmployerHistorySummary
import com.sidekick.opt_pal.data.model.H1bEmployerVerification
import com.sidekick.opt_pal.data.model.H1bEVerifyStatus
import com.sidekick.opt_pal.data.model.H1bEvidence
import com.sidekick.opt_pal.data.model.H1bProfile
import com.sidekick.opt_pal.data.model.H1bTimelineState
import com.sidekick.opt_pal.data.model.H1bCaseTracking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class H1bDashboardRepositoryImpl(
    context: Context,
    private val gson: Gson = Gson()
) : H1bDashboardRepository {

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions
    private val prefs = context.getSharedPreferences(H1B_DASHBOARD_PREFS, Context.MODE_PRIVATE)

    override fun observeProfile(uid: String): Flow<H1bProfile?> {
        return docRef(uid, PROFILE_DOCUMENT_ID)
            .snapshots()
            .map { it.toObject<H1bProfile>() }
    }

    override fun observeEmployerVerification(uid: String): Flow<H1bEmployerVerification?> {
        return docRef(uid, EMPLOYER_VERIFICATION_DOCUMENT_ID)
            .snapshots()
            .map { it.toObject<H1bEmployerVerification>() }
    }

    override fun observeTimelineState(uid: String): Flow<H1bTimelineState?> {
        return docRef(uid, TIMELINE_DOCUMENT_ID)
            .snapshots()
            .map { it.toObject<H1bTimelineState>() }
    }

    override fun observeCaseTracking(uid: String): Flow<H1bCaseTracking?> {
        return docRef(uid, CASE_TRACKING_DOCUMENT_ID)
            .snapshots()
            .map { it.toObject<H1bCaseTracking>() }
    }

    override fun observeEvidence(uid: String): Flow<H1bEvidence?> {
        return docRef(uid, EVIDENCE_DOCUMENT_ID)
            .snapshots()
            .map { it.toObject<H1bEvidence>() }
    }

    override suspend fun saveProfile(uid: String, profile: H1bProfile): Result<Unit> = runCatching {
        docRef(uid, PROFILE_DOCUMENT_ID)
            .set(profile.copy(id = PROFILE_DOCUMENT_ID, updatedAt = System.currentTimeMillis()))
            .await()
    }

    override suspend fun saveEmployerVerification(uid: String, verification: H1bEmployerVerification): Result<Unit> = runCatching {
        docRef(uid, EMPLOYER_VERIFICATION_DOCUMENT_ID)
            .set(
                verification.copy(
                    id = EMPLOYER_VERIFICATION_DOCUMENT_ID,
                    updatedAt = System.currentTimeMillis()
                )
            )
            .await()
    }

    override suspend fun saveTimelineState(uid: String, timelineState: H1bTimelineState): Result<Unit> = runCatching {
        docRef(uid, TIMELINE_DOCUMENT_ID)
            .set(
                timelineState.copy(
                    id = TIMELINE_DOCUMENT_ID,
                    updatedAt = System.currentTimeMillis()
                )
            )
            .await()
    }

    override suspend fun saveCaseTracking(uid: String, caseTracking: H1bCaseTracking): Result<Unit> = runCatching {
        docRef(uid, CASE_TRACKING_DOCUMENT_ID)
            .set(
                caseTracking.copy(
                    id = CASE_TRACKING_DOCUMENT_ID,
                    updatedAt = System.currentTimeMillis()
                )
            )
            .await()
    }

    override suspend fun saveEvidence(uid: String, evidence: H1bEvidence): Result<Unit> = runCatching {
        docRef(uid, EVIDENCE_DOCUMENT_ID)
            .set(
                evidence.copy(
                    id = EVIDENCE_DOCUMENT_ID,
                    updatedAt = System.currentTimeMillis()
                )
            )
            .await()
    }

    override fun getCachedBundle(): H1bDashboardBundle? {
        val raw = prefs.getString(KEY_BUNDLE_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, H1bDashboardBundle::class.java) }.getOrNull()
    }

    override suspend fun refreshBundle(): Result<H1bDashboardBundle> = runCatching {
        val response = functions.getHttpsCallable("getH1bDashboardBundle").call().await()
        val bundle = gson.fromJson(gson.toJson(response.data), H1bDashboardBundle::class.java)
        require(bundle.version.isNotBlank()) { "H-1B dashboard bundle version missing." }
        prefs.edit().putString(KEY_BUNDLE_JSON, gson.toJson(bundle)).apply()
        bundle
    }

    override suspend fun searchEmployerHistory(
        employerName: String,
        employerCity: String?,
        employerState: String?
    ): Result<H1bEmployerHistorySummary> = runCatching {
        val response = functions.getHttpsCallable("searchH1bEmployerHistory")
            .call(
                mapOf(
                    "employerName" to employerName,
                    "employerCity" to employerCity,
                    "employerState" to employerState
                )
            )
            .await()
        gson.fromJson(gson.toJson(response.data), H1bEmployerHistorySummary::class.java)
    }

    override suspend fun saveEVerifySnapshot(
        employerName: String,
        employerCity: String?,
        employerState: String?,
        status: H1bEVerifyStatus
    ): Result<H1bEmployerVerification> = runCatching {
        val response = functions.getHttpsCallable("saveEVerifySnapshot")
            .call(
                mapOf(
                    "employerName" to employerName,
                    "employerCity" to employerCity,
                    "employerState" to employerState,
                    "status" to status.wireValue
                )
            )
            .await()
        gson.fromJson(gson.toJson(response.data), H1bEmployerVerification::class.java)
    }

    private fun docRef(uid: String, docId: String): DocumentReference {
        return firestore.collection("users")
            .document(uid)
            .collection(H1B_DASHBOARD_COLLECTION)
            .document(docId)
    }

    private companion object {
        const val H1B_DASHBOARD_COLLECTION = "h1bDashboard"
        const val PROFILE_DOCUMENT_ID = "profile"
        const val EMPLOYER_VERIFICATION_DOCUMENT_ID = "employerVerification"
        const val TIMELINE_DOCUMENT_ID = "timelineState"
        const val CASE_TRACKING_DOCUMENT_ID = "caseTracking"
        const val EVIDENCE_DOCUMENT_ID = "evidence"
        const val H1B_DASHBOARD_PREFS = "h1b_dashboard_cache"
        const val KEY_BUNDLE_JSON = "h1b_dashboard_bundle_json"
    }
}
