package com.sidekick.opt_pal.data.repository

import android.content.Context
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.gson.Gson
import com.sidekick.opt_pal.data.model.TravelEntitlementSource
import com.sidekick.opt_pal.data.model.TravelEntitlementState
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import kotlinx.coroutines.tasks.await

class TravelAdvisorRepositoryImpl(
    context: Context,
    private val gson: Gson = Gson()
) : TravelAdvisorRepository {

    private val functions = Firebase.functions
    private val remoteConfig = Firebase.remoteConfig
    private val prefs = context.getSharedPreferences(TRAVEL_POLICY_PREFS, Context.MODE_PRIVATE)

    init {
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 60L * 60L
            }
        )
        remoteConfig.setDefaultsAsync(
            mapOf(
                REMOTE_CONFIG_OPEN_BETA to true
            )
        )
    }

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<TravelEntitlementState> {
        return runCatching {
            if (userFlag == true) {
                return@runCatching TravelEntitlementState(
                    isEnabled = true,
                    source = TravelEntitlementSource.USER_FLAG,
                    message = "Enabled directly on your profile."
                )
            }

            runCatching { remoteConfig.fetchAndActivate().await() }
            if (remoteConfig.getBoolean(REMOTE_CONFIG_OPEN_BETA)) {
                TravelEntitlementState(
                    isEnabled = true,
                    source = TravelEntitlementSource.OPEN_BETA,
                    message = "Enabled through the current beta rollout."
                )
            } else {
                TravelEntitlementState(
                    isEnabled = false,
                    source = TravelEntitlementSource.LOCKED_PREVIEW,
                    message = "Travel Risk Advisor is still in a limited rollout for beta users."
                )
            }
        }
    }

    override fun getCachedPolicyBundle(): TravelPolicyBundle? {
        val raw = prefs.getString(KEY_POLICY_BUNDLE_JSON, null) ?: return null
        return runCatching {
            gson.fromJson(raw, TravelPolicyBundle::class.java)
        }.getOrNull()
    }

    override suspend fun refreshPolicyBundle(): Result<TravelPolicyBundle> {
        return runCatching {
            val result = functions
                .getHttpsCallable("getTravelAdvisorPolicyBundle")
                .call()
                .await()
            val json = gson.toJson(result.data)
            val bundle = gson.fromJson(json, TravelPolicyBundle::class.java)
            require(bundle.version.isNotBlank()) { "Policy bundle version missing." }
            prefs.edit().putString(KEY_POLICY_BUNDLE_JSON, gson.toJson(bundle)).apply()
            bundle
        }
    }

    private companion object {
        const val TRAVEL_POLICY_PREFS = "travel_policy_cache"
        const val KEY_POLICY_BUNDLE_JSON = "travel_policy_bundle_json"
        const val REMOTE_CONFIG_OPEN_BETA = "travel_advisor_open_beta"
    }
}
