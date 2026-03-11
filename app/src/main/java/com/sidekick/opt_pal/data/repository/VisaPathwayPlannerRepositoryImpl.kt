package com.sidekick.opt_pal.data.repository

import android.content.Context
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.sidekick.opt_pal.data.model.VisaPathwayEntitlementSource
import com.sidekick.opt_pal.data.model.VisaPathwayEntitlementState
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VisaPathwayPlannerRepositoryImpl(
    context: Context,
    private val gson: Gson = Gson()
) : VisaPathwayPlannerRepository {

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions
    private val remoteConfig = Firebase.remoteConfig
    private val prefs = context.getSharedPreferences(PATHWAY_PREFS, Context.MODE_PRIVATE)

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

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<VisaPathwayEntitlementState> = runCatching {
        if (userFlag == true) {
            return@runCatching VisaPathwayEntitlementState(
                isEnabled = true,
                source = VisaPathwayEntitlementSource.USER_FLAG,
                message = "Enabled directly on your profile."
            )
        }

        runCatching { remoteConfig.fetchAndActivate().await() }
        if (remoteConfig.getBoolean(REMOTE_CONFIG_OPEN_BETA)) {
            VisaPathwayEntitlementState(
                isEnabled = true,
                source = VisaPathwayEntitlementSource.OPEN_BETA,
                message = "Enabled through the current beta rollout."
            )
        } else {
            VisaPathwayEntitlementState(
                isEnabled = false,
                source = VisaPathwayEntitlementSource.LOCKED_PREVIEW,
                message = "Visa Pathway Planner is still in a limited rollout."
            )
        }
    }

    override fun observeProfile(uid: String): Flow<VisaPathwayProfile?> {
        return firestore.collection("users")
            .document(uid)
            .collection("visaPathwayPlanner")
            .document(PROFILE_DOCUMENT_ID)
            .snapshots()
            .map { snapshot -> snapshot.toObject<VisaPathwayProfile>() }
    }

    override suspend fun saveProfile(uid: String, profile: VisaPathwayProfile): Result<Unit> = runCatching {
        firestore.collection("users")
            .document(uid)
            .collection("visaPathwayPlanner")
            .document(PROFILE_DOCUMENT_ID)
            .set(profile.copy(id = PROFILE_DOCUMENT_ID, updatedAt = System.currentTimeMillis()))
            .await()
    }

    override fun getCachedBundle(): VisaPathwayPlannerBundle? {
        val raw = prefs.getString(KEY_BUNDLE_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, VisaPathwayPlannerBundle::class.java) }.getOrNull()
    }

    override suspend fun refreshBundle(): Result<VisaPathwayPlannerBundle> = runCatching {
        val response = functions.getHttpsCallable("getVisaPathwayPlannerBundle").call().await()
        val json = gson.toJson(response.data)
        val bundle = gson.fromJson(json, VisaPathwayPlannerBundle::class.java)
        require(bundle.version.isNotBlank()) { "Visa Pathway Planner bundle version missing." }
        prefs.edit().putString(KEY_BUNDLE_JSON, gson.toJson(bundle)).apply()
        bundle
    }

    private companion object {
        const val PATHWAY_PREFS = "visa_pathway_planner_cache"
        const val KEY_BUNDLE_JSON = "visa_pathway_planner_bundle_json"
        const val REMOTE_CONFIG_OPEN_BETA = "visa_pathway_planner_open_beta"
        const val PROFILE_DOCUMENT_ID = "profile"
    }
}
