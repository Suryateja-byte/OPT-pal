package com.sidekick.opt_pal.data.repository

import android.content.Context
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983EntitlementSource
import com.sidekick.opt_pal.data.model.I983EntitlementState
import com.sidekick.opt_pal.data.model.I983ExportResult
import com.sidekick.opt_pal.data.model.I983NarrativeDraft
import com.sidekick.opt_pal.data.model.I983PolicyBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class I983AssistantRepositoryImpl(
    context: Context,
    private val gson: Gson = Gson()
) : I983AssistantRepository {

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions
    private val remoteConfig = Firebase.remoteConfig
    private val prefs = context.getSharedPreferences(I983_PREFS, Context.MODE_PRIVATE)

    init {
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 60L * 60L
            }
        )
        remoteConfig.setDefaultsAsync(
            mapOf(
                REMOTE_CONFIG_OPEN_BETA to false
            )
        )
    }

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<I983EntitlementState> = runCatching {
        if (userFlag == true) {
            return@runCatching I983EntitlementState(
                isEnabled = true,
                source = I983EntitlementSource.USER_FLAG,
                message = "Enabled directly on your profile."
            )
        }

        runCatching { remoteConfig.fetchAndActivate().await() }
        if (remoteConfig.getBoolean(REMOTE_CONFIG_OPEN_BETA)) {
            I983EntitlementState(
                isEnabled = true,
                source = I983EntitlementSource.OPEN_BETA,
                message = "Enabled through the current beta rollout."
            )
        } else {
            I983EntitlementState(
                isEnabled = false,
                source = I983EntitlementSource.LOCKED_PREVIEW,
                message = "I-983 AI Assistant is still in a limited rollout."
            )
        }
    }

    override fun getCachedPolicyBundle(): I983PolicyBundle? {
        val raw = prefs.getString(KEY_POLICY_BUNDLE_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, I983PolicyBundle::class.java) }.getOrNull()
    }

    override suspend fun refreshPolicyBundle(): Result<I983PolicyBundle> = runCatching {
        val result = functions.getHttpsCallable("getI983AssistantBundle").call().await()
        val json = gson.toJson(result.data)
        val bundle = gson.fromJson(json, I983PolicyBundle::class.java)
        require(bundle.version.isNotBlank()) { "I-983 policy bundle version missing." }
        prefs.edit().putString(KEY_POLICY_BUNDLE_JSON, gson.toJson(bundle)).apply()
        bundle
    }

    override fun observeDrafts(uid: String): Flow<List<I983Draft>> {
        return firestore.collection("users")
            .document(uid)
            .collection("i983Drafts")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<I983Draft>() }
    }

    override fun observeDraft(uid: String, draftId: String): Flow<I983Draft?> {
        return firestore.collection("users")
            .document(uid)
            .collection("i983Drafts")
            .document(draftId)
            .snapshots()
            .map { snapshot -> snapshot.toObject<I983Draft>() }
    }

    override suspend fun createDraft(uid: String, draft: I983Draft): Result<String> = runCatching {
        val docRef = firestore.collection("users")
            .document(uid)
            .collection("i983Drafts")
            .document()
        val createdAt = System.currentTimeMillis()
        docRef.set(
            draft.copy(
                id = docRef.id,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        ).await()
        docRef.id
    }

    override suspend fun updateDraft(uid: String, draft: I983Draft): Result<Unit> = runCatching {
        require(draft.id.isNotBlank()) { "draft.id is required." }
        firestore.collection("users")
            .document(uid)
            .collection("i983Drafts")
            .document(draft.id)
            .set(draft.copy(updatedAt = System.currentTimeMillis()))
            .await()
    }

    override suspend fun generateSectionDrafts(
        draftId: String,
        selectedDocumentIds: List<String>
    ): Result<I983NarrativeDraft> = runCatching {
        val response = functions
            .getHttpsCallable("generateI983SectionDrafts")
            .call(
                mapOf(
                    "draftId" to draftId,
                    "selectedDocumentIds" to selectedDocumentIds
                )
            )
            .await()
        val data = response.data as? Map<*, *> ?: error("Invalid I-983 draft response.")
        I983NarrativeDraft(
            classification = data["classification"] as? String ?: "",
            confidence = data["confidence"] as? String ?: "",
            studentRole = data["studentRole"] as? String ?: "",
            goalsAndObjectives = data["goalsAndObjectives"] as? String ?: "",
            employerOversight = data["employerOversight"] as? String ?: "",
            measuresAndAssessments = data["measuresAndAssessments"] as? String ?: "",
            annualEvaluation = data["annualEvaluation"] as? String ?: "",
            finalEvaluation = data["finalEvaluation"] as? String ?: "",
            missingInputs = (data["missingInputs"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            warnings = (data["warnings"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        )
    }

    override suspend fun exportOfficialPdf(draftId: String): Result<I983ExportResult> = runCatching {
        val response = functions
            .getHttpsCallable("exportI983OfficialPdf")
            .call(mapOf("draftId" to draftId))
            .await()
        val data = response.data as? Map<*, *> ?: error("Invalid I-983 export response.")
        I983ExportResult(
            documentId = data["documentId"] as? String ?: error("Missing documentId."),
            fileName = data["fileName"] as? String ?: error("Missing fileName."),
            generatedAt = (data["generatedAt"] as? Number)?.toLong() ?: 0L,
            templateVersion = data["templateVersion"] as? String ?: ""
        )
    }

    override suspend fun linkSignedDocument(uid: String, draftId: String, signedDocumentId: String): Result<Unit> = runCatching {
        firestore.collection("users")
            .document(uid)
            .collection("i983Drafts")
            .document(draftId)
            .update(
                mapOf(
                    "signedDocumentId" to signedDocumentId,
                    "signedLinkedAt" to System.currentTimeMillis(),
                    "status" to "signed",
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    private companion object {
        const val I983_PREFS = "i983_policy_cache"
        const val KEY_POLICY_BUNDLE_JSON = "i983_policy_bundle_json"
        const val REMOTE_CONFIG_OPEN_BETA = "i983_assistant_open_beta"
    }
}
