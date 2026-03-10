package com.sidekick.opt_pal.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.data.model.EmployerRefundOutcome
import com.sidekick.opt_pal.data.model.FicaEligibilityResult
import com.sidekick.opt_pal.data.model.FicaRefundCase
import com.sidekick.opt_pal.data.model.FicaRefundPacket
import com.sidekick.opt_pal.data.model.FicaRefundCaseStatus
import com.sidekick.opt_pal.data.model.FicaUserTaxInputs
import com.sidekick.opt_pal.data.model.W2ExtractionDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FicaRefundRepositoryImpl : FicaRefundRepository {

    private val firestore = Firebase.firestore
    private val functions = Firebase.functions

    private fun caseCollection(uid: String) =
        firestore.collection("users").document(uid).collection("ficaRefundCases")

    override fun observeCases(uid: String): Flow<List<FicaRefundCase>> {
        return caseCollection(uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<FicaRefundCase>() }
    }

    override suspend fun createCase(uid: String, w2Document: W2ExtractionDraft): Result<String> = runCatching {
        val docRef = caseCollection(uid).document()
        val now = System.currentTimeMillis()
        docRef.set(
            mapOf(
                "w2DocumentId" to w2Document.documentId,
                "taxYear" to (w2Document.taxYear ?: 0),
                "employerName" to w2Document.employerName,
                "employerOutcome" to EmployerRefundOutcome.UNKNOWN.wireValue,
                "status" to FicaRefundCaseStatus.INTAKE.wireValue,
                "createdAt" to now,
                "updatedAt" to now
            )
        ).await()
        docRef.id
    }

    override suspend fun updateUserInputs(uid: String, caseId: String, userInputs: FicaUserTaxInputs): Result<Unit> = runCatching {
        caseCollection(uid)
            .document(caseId)
            .update(
                mapOf(
                    "userInputs" to userInputs,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    override suspend fun evaluateEligibility(caseId: String): Result<FicaEligibilityResult> = runCatching {
        val response = functions
            .getHttpsCallable("evaluateFicaRefundCase")
            .call(mapOf("caseId" to caseId))
            .await()
        response.toEligibilityResult()
    }

    override suspend fun updateEmployerOutcome(
        uid: String,
        caseId: String,
        outcome: EmployerRefundOutcome
    ): Result<Unit> = runCatching {
        val nextStatus = when (outcome) {
            EmployerRefundOutcome.REFUNDED -> FicaRefundCaseStatus.CLOSED_REFUNDED.wireValue
            EmployerRefundOutcome.UNKNOWN -> FicaRefundCaseStatus.EMPLOYER_OUTREACH.wireValue
            else -> FicaRefundCaseStatus.EMPLOYER_OUTREACH.wireValue
        }
        caseCollection(uid)
            .document(caseId)
            .update(
                mapOf(
                    "employerOutcome" to outcome.wireValue,
                    "status" to nextStatus,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    override suspend fun generateEmployerPacket(caseId: String): Result<FicaRefundPacket> = runCatching {
        val response = functions
            .getHttpsCallable("generateFicaEmployerPacket")
            .call(mapOf("caseId" to caseId))
            .await()
        response.toPacket()
    }

    override suspend fun generateIrsPacket(
        caseId: String,
        fullSsn: String,
        fullEmployerEin: String,
        mailingAddress: String
    ): Result<FicaRefundPacket> = runCatching {
        val response = functions
            .getHttpsCallable("generateFicaRefundPacket")
            .call(
                mapOf(
                    "caseId" to caseId,
                    "fullSsn" to fullSsn,
                    "fullEmployerEin" to fullEmployerEin,
                    "mailingAddress" to mailingAddress
                )
            )
            .await()
        response.toPacket()
    }

    override suspend fun archiveCase(uid: String, caseId: String): Result<Unit> = runCatching {
        caseCollection(uid)
            .document(caseId)
            .update(
                mapOf(
                    "status" to FicaRefundCaseStatus.CLOSED_OUT_OF_SCOPE.wireValue,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }
}

private fun com.google.firebase.functions.HttpsCallableResult.toEligibilityResult(): FicaEligibilityResult {
    val data = this.data as? Map<*, *> ?: error("Invalid FICA eligibility response.")
    return FicaEligibilityResult(
        classification = data["classification"] as? String ?: "",
        refundAmount = (data["refundAmount"] as? Number)?.toDouble(),
        eligibilityReasons = (data["eligibilityReasons"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        blockingIssues = (data["blockingIssues"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        requiredAttachments = (data["requiredAttachments"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        recommendedNextStep = data["recommendedNextStep"] as? String ?: "",
        statuteWarning = data["statuteWarning"] as? String ?: ""
    )
}

private fun com.google.firebase.functions.HttpsCallableResult.toPacket(): FicaRefundPacket {
    val data = this.data as? Map<*, *> ?: error("Invalid FICA packet response.")
    val packet = data["packet"] as? Map<*, *> ?: error("Missing packet payload.")
    return FicaRefundPacket(
        documentId = packet["documentId"] as? String ?: "",
        fileName = packet["fileName"] as? String ?: "",
        generatedAt = (packet["generatedAt"] as? Number)?.toLong() ?: 0L,
        kind = packet["kind"] as? String ?: ""
    )
}
