package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.FeedbackEntry

interface FeedbackRepository {
    suspend fun submitFeedback(entry: FeedbackEntry): Result<Unit>
}
