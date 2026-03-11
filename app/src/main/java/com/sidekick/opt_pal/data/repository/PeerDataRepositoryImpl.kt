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
import com.sidekick.opt_pal.data.model.PeerDataBundle
import com.sidekick.opt_pal.data.model.PeerDataEntitlementSource
import com.sidekick.opt_pal.data.model.PeerDataEntitlementState
import com.sidekick.opt_pal.data.model.PeerDataParticipationSettings
import com.sidekick.opt_pal.data.model.PeerDataSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PeerDataRepositoryImpl(
    context: Context,
    private val gson: Gson = Gson()
) : PeerDataRepository {

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions
    private val remoteConfig = Firebase.remoteConfig
    private val prefs = context.getSharedPreferences(PEER_DATA_PREFS, Context.MODE_PRIVATE)

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

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<PeerDataEntitlementState> = runCatching {
        if (userFlag == true) {
            return@runCatching PeerDataEntitlementState(
                isEnabled = true,
                source = PeerDataEntitlementSource.USER_FLAG,
                message = "Enabled directly on your profile."
            )
        }

        runCatching { remoteConfig.fetchAndActivate().await() }
        if (remoteConfig.getBoolean(REMOTE_CONFIG_OPEN_BETA)) {
            PeerDataEntitlementState(
                isEnabled = true,
                source = PeerDataEntitlementSource.OPEN_BETA,
                message = "Enabled through the current beta rollout."
            )
        } else {
            PeerDataEntitlementState(
                isEnabled = false,
                source = PeerDataEntitlementSource.LOCKED_PREVIEW,
                message = "Peer Data is still in a limited rollout."
            )
        }
    }

    override fun observeParticipationSettings(uid: String): Flow<PeerDataParticipationSettings> {
        return firestore.collection("users")
            .document(uid)
            .collection(PEER_DATA_COLLECTION)
            .document(SETTINGS_DOCUMENT_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject<PeerDataParticipationSettings>()
                    ?: PeerDataParticipationSettings()
            }
    }

    override suspend fun saveParticipationSettings(
        contributionEnabled: Boolean,
        previewedAt: Long?
    ): Result<PeerDataParticipationSettings> = runCatching {
        val response = functions.getHttpsCallable("savePeerDataParticipation")
            .call(
                mapOf(
                    "contributionEnabled" to contributionEnabled,
                    "previewedAt" to previewedAt
                )
            )
            .await()
        gson.fromJson(gson.toJson(response.data), PeerDataParticipationSettings::class.java)
    }

    override fun getCachedBundle(): PeerDataBundle? {
        val raw = prefs.getString(KEY_BUNDLE_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, PeerDataBundle::class.java) }.getOrNull()
    }

    override suspend fun refreshBundle(): Result<PeerDataBundle> = runCatching {
        val response = functions.getHttpsCallable("getPeerDataBundle").call().await()
        val bundle = gson.fromJson(gson.toJson(response.data), PeerDataBundle::class.java)
        require(bundle.version.isNotBlank()) { "Peer Data bundle version missing." }
        prefs.edit().putString(KEY_BUNDLE_JSON, gson.toJson(bundle)).apply()
        bundle
    }

    override fun getCachedSnapshot(): PeerDataSnapshot? {
        val raw = prefs.getString(KEY_SNAPSHOT_JSON, null) ?: return null
        return runCatching { gson.fromJson(raw, PeerDataSnapshot::class.java) }.getOrNull()
    }

    override suspend fun getPeerSnapshot(): Result<PeerDataSnapshot> = runCatching {
        val response = functions.getHttpsCallable("getPeerDataSnapshot").call().await()
        val snapshot = gson.fromJson(gson.toJson(response.data), PeerDataSnapshot::class.java)
        prefs.edit().putString(KEY_SNAPSHOT_JSON, gson.toJson(snapshot)).apply()
        snapshot
    }

    private companion object {
        const val PEER_DATA_COLLECTION = "peerData"
        const val SETTINGS_DOCUMENT_ID = "settings"
        const val PEER_DATA_PREFS = "peer_data_cache"
        const val KEY_BUNDLE_JSON = "peer_data_bundle_json"
        const val KEY_SNAPSHOT_JSON = "peer_data_snapshot_json"
        const val REMOTE_CONFIG_OPEN_BETA = "peer_data_open_beta"
    }
}
