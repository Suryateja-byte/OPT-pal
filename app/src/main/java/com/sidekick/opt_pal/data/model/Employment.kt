package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

data class Employment(
    @DocumentId val id: String = "",
    val employerName: String = "",
    val startDate: Long = 0L,
    val endDate: Long? = null,
    val jobTitle: String = "",
    val hoursPerWeek: Int? = null
)
