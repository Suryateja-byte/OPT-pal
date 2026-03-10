package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

enum class ReportableEventType(val description: String) {
    NEW_EMPLOYER("New Employer"),
    EMPLOYER_ENDED("Employer Ended"),
    ADDRESS_CHANGE("Address Change"),
    OTHER("Other")
}

enum class ReportingSource { AUTO, MANUAL }

data class ReportingObligation(
    @DocumentId val id: String = "",
    val eventType: String = ReportableEventType.OTHER.name,
    val description: String = "",
    val eventDate: Long = 0L,
    val dueDate: Long = 0L,
    val isCompleted: Boolean = false,
    val createdBy: String = ReportingSource.AUTO.name
)
