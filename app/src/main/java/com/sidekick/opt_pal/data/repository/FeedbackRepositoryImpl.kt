package com.sidekick.opt_pal.data.repository

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.data.model.FeedbackEntry
import kotlinx.coroutines.tasks.await

class FeedbackRepositoryImpl : FeedbackRepository {

    private val collection = Firebase.firestore.collection("feedback")

    override suspend fun submitFeedback(entry: FeedbackEntry): Result<Unit> = runCatching {
        collection.add(
            entry.copy(createdAt = System.currentTimeMillis())
        ).await()
    }
}
