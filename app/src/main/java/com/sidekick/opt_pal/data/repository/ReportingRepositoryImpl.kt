package com.sidekick.opt_pal.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.data.model.ReportingDraftResult
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingWizard
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.model.ReportingWizardInput
import com.sidekick.opt_pal.data.model.ReportingWizardStartResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ReportingRepositoryImpl : ReportingRepository {

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions
    private val usersCollection = firestore.collection("users")

    private fun reportingCollection(uid: String) =
        usersCollection.document(uid).collection("reporting")

    private fun wizardCollection(uid: String) =
        usersCollection.document(uid).collection("reportingWizards")

    override fun getReportingObligations(uid: String): Flow<List<ReportingObligation>> {
        return reportingCollection(uid)
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<ReportingObligation>() }
    }

    override fun getReportingWizards(uid: String): Flow<List<ReportingWizard>> {
        return wizardCollection(uid)
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<ReportingWizard>() }
    }

    override fun getReportingWizard(uid: String, wizardId: String): Flow<ReportingWizard?> {
        return wizardCollection(uid)
            .document(wizardId)
            .snapshots()
            .map { snapshot -> snapshot.toObject<ReportingWizard>() }
    }

    override suspend fun addObligation(
        uid: String,
        obligation: ReportingObligation
    ): Result<Unit> = runCatching {
        reportingCollection(uid).document().set(obligation).await()
    }

    override suspend fun toggleObligationStatus(
        uid: String,
        obligationId: String,
        isCompleted: Boolean
    ): Result<Unit> = runCatching {
        reportingCollection(uid).document(obligationId)
            .update("isCompleted", isCompleted)
            .await()
    }

    override suspend fun deleteObligation(uid: String, obligationId: String): Result<Unit> = runCatching {
        reportingCollection(uid).document(obligationId).delete().await()
    }

    override suspend fun updateObligation(uid: String, obligation: ReportingObligation): Result<Unit> = runCatching {
        val docId = obligation.id.ifBlank { reportingCollection(uid).document().id }
        reportingCollection(uid).document(docId).set(obligation.copy(id = docId)).await()
    }

    override suspend fun getObligation(uid: String, obligationId: String): Result<ReportingObligation?> = runCatching {
        reportingCollection(uid).document(obligationId).get().await().toObject<ReportingObligation>()
    }

    override suspend fun startWizard(
        uid: String,
        eventType: ReportingWizardEventType,
        relatedEmploymentId: String,
        eventDate: Long
    ): Result<ReportingWizardStartResult> = runCatching {
        val response = functions
            .getHttpsCallable("prepareReportingWizard")
            .call(
                mapOf(
                    "eventType" to eventType.wireValue,
                    "relatedEmploymentId" to relatedEmploymentId,
                    "eventDate" to eventDate
                )
            )
            .await()

        response.toWizardStartResult()
    }

    override suspend fun seedWizardFromObligation(uid: String, obligationId: String): Result<ReportingWizardStartResult> = runCatching {
        val response = functions
            .getHttpsCallable("prepareReportingWizard")
            .call(mapOf("obligationId" to obligationId))
            .await()

        response.toWizardStartResult()
    }

    override suspend fun updateWizardUserInputs(
        uid: String,
        wizardId: String,
        userInputs: ReportingWizardInput
    ): Result<Unit> = runCatching {
        wizardCollection(uid).document(wizardId)
            .update(
                mapOf(
                    "userInputs" to userInputs,
                    "status" to "ready"
                )
            )
            .await()
    }

    override suspend fun updateWizardEditedDraft(uid: String, wizardId: String, editedDraft: String): Result<Unit> = runCatching {
        wizardCollection(uid).document(wizardId)
            .update("editedDraft", editedDraft)
            .await()
    }

    override suspend fun markDraftCopied(uid: String, wizardId: String): Result<Unit> = runCatching {
        wizardCollection(uid).document(wizardId)
            .update("copiedAt", System.currentTimeMillis())
            .await()
    }

    override suspend fun completeWizard(uid: String, wizardId: String): Result<Unit> = runCatching {
        functions
            .getHttpsCallable("completeReportingWizard")
            .call(mapOf("wizardId" to wizardId))
            .await()
    }

    override suspend fun generateRelationshipDraft(
        wizardId: String,
        selectedDocumentIds: List<String>
    ): Result<ReportingDraftResult> = runCatching {
        val response = functions
            .getHttpsCallable("generateSevpRelationshipDraft")
            .call(
                mapOf(
                    "wizardId" to wizardId,
                    "selectedDocumentIds" to selectedDocumentIds
                )
            )
            .await()

        val data = response.data as? Map<*, *> ?: error("Invalid draft response.")
        ReportingDraftResult(
            classification = data["classification"] as? String ?: "",
            confidence = data["confidence"] as? String ?: "",
            draftParagraph = data["draftParagraph"] as? String ?: "",
            whyThisDraftFits = (data["whyThisDraftFits"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            missingInputs = (data["missingInputs"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
            warnings = (data["warnings"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        )
    }
}

private fun com.google.firebase.functions.HttpsCallableResult.toWizardStartResult(): ReportingWizardStartResult {
    val data = this.data as? Map<*, *> ?: error("Invalid reporting wizard response.")
    val wizardId = data["wizardId"] as? String ?: error("Missing wizardId.")
    val obligationId = data["obligationId"] as? String ?: error("Missing obligationId.")
    return ReportingWizardStartResult(
        wizardId = wizardId,
        obligationId = obligationId
    )
}
