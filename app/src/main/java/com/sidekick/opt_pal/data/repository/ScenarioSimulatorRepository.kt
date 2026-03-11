package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.ScenarioSimulatorBundle
import com.sidekick.opt_pal.data.model.ScenarioSimulatorEntitlementState
import kotlinx.coroutines.flow.Flow

interface ScenarioSimulatorRepository {
    suspend fun resolveEntitlement(userFlag: Boolean?): Result<ScenarioSimulatorEntitlementState>
    fun observeDrafts(uid: String): Flow<List<ScenarioDraft>>
    suspend fun saveDraft(uid: String, draft: ScenarioDraft): Result<String>
    suspend fun deleteDraft(uid: String, scenarioId: String): Result<Unit>
    fun getCachedBundle(): ScenarioSimulatorBundle?
    suspend fun refreshBundle(): Result<ScenarioSimulatorBundle>
}
