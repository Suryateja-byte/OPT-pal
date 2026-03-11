package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.PeerDataBundle
import com.sidekick.opt_pal.data.model.PeerDataEntitlementState
import com.sidekick.opt_pal.data.model.PeerDataParticipationSettings
import com.sidekick.opt_pal.data.model.PeerDataSnapshot
import kotlinx.coroutines.flow.Flow

interface PeerDataRepository {
    suspend fun resolveEntitlement(userFlag: Boolean?): Result<PeerDataEntitlementState>
    fun observeParticipationSettings(uid: String): Flow<PeerDataParticipationSettings>
    suspend fun saveParticipationSettings(
        contributionEnabled: Boolean,
        previewedAt: Long? = null
    ): Result<PeerDataParticipationSettings>
    fun getCachedBundle(): PeerDataBundle?
    suspend fun refreshBundle(): Result<PeerDataBundle>
    fun getCachedSnapshot(): PeerDataSnapshot?
    suspend fun getPeerSnapshot(): Result<PeerDataSnapshot>
}
