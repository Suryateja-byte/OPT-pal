package com.sidekick.opt_pal.data.repository

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.sidekick.opt_pal.data.model.ScenarioBaselineFingerprint
import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.ScenarioDraftOutcomeSummary
import com.sidekick.opt_pal.data.model.ScenarioEntitlementSource
import com.sidekick.opt_pal.data.model.ScenarioSimulatorBundle
import com.sidekick.opt_pal.data.model.ScenarioSimulatorEntitlementState
import com.sidekick.opt_pal.data.model.defaultDraftName
import com.sidekick.opt_pal.data.model.scenarioAssumptionsFromStorage
import com.sidekick.opt_pal.data.model.toStorageMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ScenarioSimulatorRepositoryImpl(
    context: Context,
    private val gson: Gson = Gson()
) : ScenarioSimulatorRepository {

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions
    private val remoteConfig = Firebase.remoteConfig
    private val prefs = context.getSharedPreferences(SCENARIO_PREFS, Context.MODE_PRIVATE)

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

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<ScenarioSimulatorEntitlementState> = runCatching {
        if (userFlag == true) {
            return@runCatching ScenarioSimulatorEntitlementState(
                isEnabled = true,
                source = ScenarioEntitlementSource.USER_FLAG,
                message = "Enabled directly on your profile."
            )
        }

        runCatching { remoteConfig.fetchAndActivate().await() }
        if (remoteConfig.getBoolean(REMOTE_CONFIG_OPEN_BETA)) {
            ScenarioSimulatorEntitlementState(
                isEnabled = true,
                source = ScenarioEntitlementSource.OPEN_BETA,
                message = "Enabled through the current beta rollout."
            )
        } else {
            ScenarioSimulatorEntitlementState(
                isEnabled = false,
                source = ScenarioEntitlementSource.LOCKED_PREVIEW,
                message = "Scenario Simulator is still in a limited rollout."
            )
        }
    }

    override fun observeDrafts(uid: String): Flow<List<ScenarioDraft>> {
        return firestore.collection("users")
            .document(uid)
            .collection(SCENARIO_COLLECTION)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents
                    .mapNotNull(::toScenarioDraft)
                    .sortedWith(
                        compareByDescending<ScenarioDraft> { it.pinned }
                            .thenByDescending { it.updatedAt }
                    )
            }
    }

    override suspend fun saveDraft(uid: String, draft: ScenarioDraft): Result<String> = runCatching {
        val now = System.currentTimeMillis()
        val docRef = if (draft.id.isBlank()) {
            firestore.collection("users")
                .document(uid)
                .collection(SCENARIO_COLLECTION)
                .document()
        } else {
            firestore.collection("users")
                .document(uid)
                .collection(SCENARIO_COLLECTION)
                .document(draft.id)
        }
        val draftId = docRef.id
        val createdAt = draft.createdAt.takeIf { it > 0L } ?: now
        docRef.set(
            buildMap<String, Any> {
                put("templateId", draft.parsedTemplateId.wireValue)
                put("name", draft.name)
                put("assumptions", draft.assumptions.toStorageMap())
                put("createdAt", createdAt)
                put("updatedAt", now)
                draft.lastRunAt?.let { put("lastRunAt", it) }
                put("baselineFingerprint", draft.baselineFingerprint.value)
                draft.lastOutcome?.let { put("lastOutcome", it.toStorageMap()) }
                draft.archivedAt?.let { put("archivedAt", it) }
                put("pinned", draft.pinned)
            }
        ).await()
        draftId
    }

    override suspend fun deleteDraft(uid: String, scenarioId: String): Result<Unit> = runCatching {
        firestore.collection("users")
            .document(uid)
            .collection(SCENARIO_COLLECTION)
            .document(scenarioId)
            .delete()
            .await()
    }

    override fun getCachedBundle(): ScenarioSimulatorBundle? {
        val raw = prefs.getString(KEY_BUNDLE_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, ScenarioSimulatorBundle::class.java) }.getOrNull()
    }

    override suspend fun refreshBundle(): Result<ScenarioSimulatorBundle> = runCatching {
        val response = functions.getHttpsCallable("getScenarioSimulatorBundle").call().await()
        val bundle = gson.fromJson(gson.toJson(response.data), ScenarioSimulatorBundle::class.java)
        require(bundle.version.isNotBlank()) { "Scenario Simulator bundle version missing." }
        prefs.edit().putString(KEY_BUNDLE_JSON, gson.toJson(bundle)).apply()
        bundle
    }

    private fun toScenarioDraft(snapshot: DocumentSnapshot): ScenarioDraft? {
        val data = snapshot.data ?: return null
        val templateId = com.sidekick.opt_pal.data.model.ScenarioTemplateId.fromWireValue(
            data["templateId"] as? String
        )
        val assumptions = scenarioAssumptionsFromStorage(
            templateId = templateId,
            raw = data["assumptions"] as? Map<String, Any>
        )
        return ScenarioDraft(
            id = snapshot.id,
            templateId = templateId.wireValue,
            name = data["name"] as? String ?: templateId.defaultDraftName(),
            assumptions = assumptions,
            createdAt = numberToLong(data["createdAt"]),
            updatedAt = numberToLong(data["updatedAt"]),
            lastRunAt = data["lastRunAt"]?.let(::numberToLong),
            baselineFingerprint = ScenarioBaselineFingerprint(
                value = when (val raw = data["baselineFingerprint"]) {
                    is String -> raw
                    is Map<*, *> -> raw["value"] as? String ?: ""
                    else -> ""
                }
            ),
            lastOutcome = (data["lastOutcome"] as? Map<String, Any>)?.toOutcomeSummary(),
            archivedAt = data["archivedAt"]?.let(::numberToLong),
            pinned = data["pinned"] as? Boolean ?: false
        )
    }

    private fun ScenarioDraftOutcomeSummary.toStorageMap(): Map<String, Any> {
        return buildMap {
            put("outcome", outcome)
            put("headline", headline)
            put("confidence", confidence)
            put("computedAt", computedAt)
        }
    }

    private fun Map<String, Any>.toOutcomeSummary(): ScenarioDraftOutcomeSummary {
        return ScenarioDraftOutcomeSummary(
            outcome = this["outcome"] as? String ?: com.sidekick.opt_pal.data.model.ScenarioOutcome.ACTION_REQUIRED.name,
            headline = this["headline"] as? String ?: "",
            confidence = this["confidence"] as? String ?: com.sidekick.opt_pal.data.model.ScenarioConfidence.HYPOTHETICAL.name,
            computedAt = numberToLong(this["computedAt"])
        )
    }

    private fun numberToLong(value: Any?): Long {
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is Number -> value.toLong()
            else -> 0L
        }
    }

    private companion object {
        const val SCENARIO_COLLECTION = "scenarioSimulatorDrafts"
        const val SCENARIO_PREFS = "scenario_simulator_cache"
        const val KEY_BUNDLE_JSON = "scenario_simulator_bundle_json"
        const val REMOTE_CONFIG_OPEN_BETA = "scenario_simulator_open_beta"
    }
}
