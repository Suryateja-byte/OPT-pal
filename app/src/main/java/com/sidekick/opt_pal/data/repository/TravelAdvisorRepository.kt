package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.TravelEntitlementState
import com.sidekick.opt_pal.data.model.TravelPolicyBundle

interface TravelAdvisorRepository {
    suspend fun resolveEntitlement(userFlag: Boolean?): Result<TravelEntitlementState>
    fun getCachedPolicyBundle(): TravelPolicyBundle?
    suspend fun refreshPolicyBundle(): Result<TravelPolicyBundle>
}
