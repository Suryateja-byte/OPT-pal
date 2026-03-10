package com.sidekick.opt_pal.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.data.model.Employment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DashboardRepositoryImpl : DashboardRepository {

    private val firestore = Firebase.firestore
    private val usersCollection = firestore.collection("users")

    private fun employmentCollection(uid: String) =
        usersCollection.document(uid).collection("employment")

    override fun getEmployments(uid: String): Flow<List<Employment>> {
        return employmentCollection(uid)
            .orderBy("startDate", Query.Direction.DESCENDING)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects<Employment>()
            }
    }

    override suspend fun addEmployment(uid: String, employment: Employment): Result<Unit> {
        return runCatching {
            val document = if (employment.id.isBlank()) {
                employmentCollection(uid).document()
            } else {
                employmentCollection(uid).document(employment.id)
            }
            document.set(employment).await()
        }
    }

    override suspend fun deleteEmployment(uid: String, employmentId: String): Result<Unit> {
        return runCatching {
            employmentCollection(uid).document(employmentId).delete().await()
        }
    }

    override suspend fun getEmployment(uid: String, employmentId: String): Result<Employment?> {
        return runCatching {
            employmentCollection(uid)
                .document(employmentId)
                .get()
                .await()
                .toObject<Employment>()
        }
    }
}
