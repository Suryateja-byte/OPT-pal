package com.sidekick.opt_pal.data.repository

import com.sidekick.opt_pal.data.model.EmployerRefundOutcome
import com.sidekick.opt_pal.data.model.FicaEligibilityResult
import com.sidekick.opt_pal.data.model.FicaRefundCase
import com.sidekick.opt_pal.data.model.FicaRefundPacket
import com.sidekick.opt_pal.data.model.FicaUserTaxInputs
import com.sidekick.opt_pal.data.model.W2ExtractionDraft
import kotlinx.coroutines.flow.Flow

interface FicaRefundRepository {
    fun observeCases(uid: String): Flow<List<FicaRefundCase>>
    suspend fun createCase(uid: String, w2Document: W2ExtractionDraft): Result<String>
    suspend fun updateUserInputs(uid: String, caseId: String, userInputs: FicaUserTaxInputs): Result<Unit>
    suspend fun evaluateEligibility(caseId: String): Result<FicaEligibilityResult>
    suspend fun updateEmployerOutcome(uid: String, caseId: String, outcome: EmployerRefundOutcome): Result<Unit>
    suspend fun generateEmployerPacket(caseId: String): Result<FicaRefundPacket>
    suspend fun generateIrsPacket(
        caseId: String,
        fullSsn: String,
        fullEmployerEin: String,
        mailingAddress: String
    ): Result<FicaRefundPacket>
    suspend fun archiveCase(uid: String, caseId: String): Result<Unit>
}
