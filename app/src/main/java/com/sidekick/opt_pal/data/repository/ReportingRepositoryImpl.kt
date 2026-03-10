package com.sidekick.opt_pal.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.data.model.ReportingObligation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ReportingRepositoryImpl : ReportingRepository {

    private val firestore = Firebase.firestore
    private val usersCollection = firestore.collection("users")

    private fun reportingCollection(uid: String) =
        usersCollection.document(uid).collection("reporting")

    override fun getReportingObligations(uid: String): Flow<List<ReportingObligation>> {
        return reportingCollection(uid)
            .orderBy("dueDate", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<ReportingObligation>() }
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
}
