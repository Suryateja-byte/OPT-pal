package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.VisaPathwayEntitlementState
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import kotlinx.coroutines.flow.Flow

interface VisaPathwayPlannerRepository {
    suspend fun resolveEntitlement(userFlag: Boolean?): Result<VisaPathwayEntitlementState>
    fun observeProfile(uid: String): Flow<VisaPathwayProfile?>
    suspend fun saveProfile(uid: String, profile: VisaPathwayProfile): Result<Unit>
    fun getCachedBundle(): VisaPathwayPlannerBundle?
    suspend fun refreshBundle(): Result<VisaPathwayPlannerBundle>
}
