package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Represents a single beta feedback submission captured inside the app.
 */
data class FeedbackEntry(
    @DocumentId val id: String = "",
    val uid: String? = null,
    val message: String = "",
    val contactEmail: String? = null,
    val includeLogs: Boolean = true,
    val deviceInfo: String? = null,
    val rating: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)
