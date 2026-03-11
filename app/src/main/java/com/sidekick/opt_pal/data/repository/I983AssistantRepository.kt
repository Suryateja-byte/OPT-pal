package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983EntitlementState
import com.sidekick.opt_pal.data.model.I983ExportResult
import com.sidekick.opt_pal.data.model.I983NarrativeDraft
import com.sidekick.opt_pal.data.model.I983PolicyBundle
import kotlinx.coroutines.flow.Flow

interface I983AssistantRepository {
    suspend fun resolveEntitlement(userFlag: Boolean?): Result<I983EntitlementState>
    fun getCachedPolicyBundle(): I983PolicyBundle?
    suspend fun refreshPolicyBundle(): Result<I983PolicyBundle>
    fun observeDrafts(uid: String): Flow<List<I983Draft>>
    fun observeDraft(uid: String, draftId: String): Flow<I983Draft?>
    suspend fun createDraft(uid: String, draft: I983Draft): Result<String>
    suspend fun updateDraft(uid: String, draft: I983Draft): Result<Unit>
    suspend fun generateSectionDrafts(draftId: String, selectedDocumentIds: List<String>): Result<I983NarrativeDraft>
    suspend fun exportOfficialPdf(draftId: String): Result<I983ExportResult>
    suspend fun linkSignedDocument(uid: String, draftId: String, signedDocumentId: String): Result<Unit>
}
