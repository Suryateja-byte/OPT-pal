package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingDraftResult
import com.sidekick.opt_pal.data.model.ReportingWizard
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.model.ReportingWizardInput
import com.sidekick.opt_pal.data.model.ReportingWizardStartResult
import kotlinx.coroutines.flow.Flow

interface ReportingRepository {
    fun getReportingObligations(uid: String): Flow<List<ReportingObligation>>
    fun getReportingWizards(uid: String): Flow<List<ReportingWizard>>
    fun getReportingWizard(uid: String, wizardId: String): Flow<ReportingWizard?>
    suspend fun addObligation(uid: String, obligation: ReportingObligation): Result<Unit>
    suspend fun toggleObligationStatus(uid: String, obligationId: String, isCompleted: Boolean): Result<Unit>
    suspend fun deleteObligation(uid: String, obligationId: String): Result<Unit>
    suspend fun updateObligation(uid: String, obligation: ReportingObligation): Result<Unit>
    suspend fun getObligation(uid: String, obligationId: String): Result<ReportingObligation?>
    suspend fun startWizard(
        uid: String,
        eventType: ReportingWizardEventType,
        relatedEmploymentId: String,
        eventDate: Long
    ): Result<ReportingWizardStartResult>
    suspend fun seedWizardFromObligation(uid: String, obligationId: String): Result<ReportingWizardStartResult>
    suspend fun updateWizardUserInputs(
        uid: String,
        wizardId: String,
        userInputs: ReportingWizardInput
    ): Result<Unit>
    suspend fun updateWizardEditedDraft(uid: String, wizardId: String, editedDraft: String): Result<Unit>
    suspend fun markDraftCopied(uid: String, wizardId: String): Result<Unit>
    suspend fun completeWizard(uid: String, wizardId: String): Result<Unit>
    suspend fun generateRelationshipDraft(
        wizardId: String,
        selectedDocumentIds: List<String>
    ): Result<ReportingDraftResult>
}
