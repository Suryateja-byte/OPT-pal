package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bEmployerHistorySummary
import com.sidekick.opt_pal.data.model.H1bEmployerVerification
import com.sidekick.opt_pal.data.model.H1bEVerifyStatus
import com.sidekick.opt_pal.data.model.H1bEvidence
import com.sidekick.opt_pal.data.model.H1bProfile
import com.sidekick.opt_pal.data.model.H1bTimelineState
import com.sidekick.opt_pal.data.model.H1bCaseTracking
import kotlinx.coroutines.flow.Flow

interface H1bDashboardRepository {
    fun observeProfile(uid: String): Flow<H1bProfile?>
    fun observeEmployerVerification(uid: String): Flow<H1bEmployerVerification?>
    fun observeTimelineState(uid: String): Flow<H1bTimelineState?>
    fun observeCaseTracking(uid: String): Flow<H1bCaseTracking?>
    fun observeEvidence(uid: String): Flow<H1bEvidence?>

    suspend fun saveProfile(uid: String, profile: H1bProfile): Result<Unit>
    suspend fun saveEmployerVerification(uid: String, verification: H1bEmployerVerification): Result<Unit>
    suspend fun saveTimelineState(uid: String, timelineState: H1bTimelineState): Result<Unit>
    suspend fun saveCaseTracking(uid: String, caseTracking: H1bCaseTracking): Result<Unit>
    suspend fun saveEvidence(uid: String, evidence: H1bEvidence): Result<Unit>

    fun getCachedBundle(): H1bDashboardBundle?
    suspend fun refreshBundle(): Result<H1bDashboardBundle>
    suspend fun searchEmployerHistory(
        employerName: String,
        employerCity: String? = null,
        employerState: String? = null
    ): Result<H1bEmployerHistorySummary>

    suspend fun saveEVerifySnapshot(
        employerName: String,
        employerCity: String? = null,
        employerState: String? = null,
        status: H1bEVerifyStatus
    ): Result<H1bEmployerVerification>
}
