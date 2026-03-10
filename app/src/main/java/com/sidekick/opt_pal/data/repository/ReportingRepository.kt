package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.ReportingObligation
import kotlinx.coroutines.flow.Flow

interface ReportingRepository {
    fun getReportingObligations(uid: String): Flow<List<ReportingObligation>>
    suspend fun addObligation(uid: String, obligation: ReportingObligation): Result<Unit>
    suspend fun toggleObligationStatus(uid: String, obligationId: String, isCompleted: Boolean): Result<Unit>
    suspend fun deleteObligation(uid: String, obligationId: String): Result<Unit>
    suspend fun updateObligation(uid: String, obligation: ReportingObligation): Result<Unit>
    suspend fun getObligation(uid: String, obligationId: String): Result<ReportingObligation?>
}
