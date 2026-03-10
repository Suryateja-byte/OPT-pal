package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.ComplianceHealthAvailability
import com.sidekick.opt_pal.data.model.ComplianceScoreSnapshotState

interface ComplianceHealthRepository {
    suspend fun resolveAvailability(): Result<ComplianceHealthAvailability>
    fun syncSnapshot(uid: String, score: Int, computedAt: Long): ComplianceScoreSnapshotState
}
