package com.sidekick.opt_pal.testing.fakes

import android.content.ContentResolver
import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.CompleteSetupRequest
import com.sidekick.opt_pal.data.model.DocumentCategory
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.model.EmployerRefundOutcome
import com.sidekick.opt_pal.data.model.SecureDocumentContent
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.FicaEligibilityClassification
import com.sidekick.opt_pal.data.model.FicaEligibilityResult
import com.sidekick.opt_pal.data.model.FicaRefundCase
import com.sidekick.opt_pal.data.model.FicaRefundPacket
import com.sidekick.opt_pal.data.model.FicaUserTaxInputs
import com.sidekick.opt_pal.data.model.ReportingDraftResult
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingWizard
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.model.ReportingWizardInput
import com.sidekick.opt_pal.data.model.ReportingWizardStartResult
import com.sidekick.opt_pal.data.model.TravelEntitlementSource
import com.sidekick.opt_pal.data.model.TravelEntitlementState
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import com.sidekick.opt_pal.data.model.UscisCaseRefreshResult
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UscisTrackerAvailability
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.W2ExtractionDraft
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.FicaRefundRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.data.repository.TravelAdvisorRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAuthRepository : AuthRepository {
    private val authState = MutableStateFlow<FirebaseUser?>(null)
    private val profiles = mutableMapOf<String, MutableStateFlow<UserProfile?>>()
    var completeSetupResult: Result<Unit> = Result.success(Unit)
    val completedSetupRequests = mutableListOf<CompleteSetupRequest>()
    var updateUnemploymentTrackingStartDateResult: Result<Unit> = Result.success(Unit)

    override fun getAuthState(): Flow<FirebaseUser?> = authState

    override fun getUserProfile(uid: String): Flow<UserProfile?> =
        profiles.getOrPut(uid) { MutableStateFlow(null) }

    override suspend fun getUserProfileSnapshot(uid: String): Result<UserProfile?> {
        return Result.success(profiles[uid]?.value)
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)

    override suspend fun register(email: String, password: String): Result<Unit> = Result.success(Unit)

    override suspend fun completeSetup(request: CompleteSetupRequest): Result<Unit> {
        completedSetupRequests += request
        return completeSetupResult
    }

    override suspend fun updateUnemploymentTrackingStartDate(dateMillis: Long): Result<Unit> {
        val currentUser = authState.value ?: return Result.failure(IllegalStateException("No authenticated user"))
        val currentProfile = profiles.getOrPut(currentUser.uid) { MutableStateFlow(null) }.value
            ?: UserProfile(uid = currentUser.uid)
        profiles.getOrPut(currentUser.uid) { MutableStateFlow(null) }.value = currentProfile.copy(
            unemploymentTrackingStartDate = dateMillis
        )
        return updateUnemploymentTrackingStartDateResult
    }

    override suspend fun updateMajorName(majorName: String): Result<Unit> {
        val currentUser = authState.value ?: return Result.failure(IllegalStateException("No authenticated user"))
        val currentProfile = profiles.getOrPut(currentUser.uid) { MutableStateFlow(null) }.value
            ?: UserProfile(uid = currentUser.uid)
        profiles.getOrPut(currentUser.uid) { MutableStateFlow(null) }.value = currentProfile.copy(
            majorName = majorName
        )
        return Result.success(Unit)
    }

    override suspend fun updateFirstUsStudentTaxYear(taxYear: Int): Result<Unit> {
        val currentUser = authState.value ?: return Result.failure(IllegalStateException("No authenticated user"))
        val currentProfile = profiles.getOrPut(currentUser.uid) { MutableStateFlow(null) }.value
            ?: UserProfile(uid = currentUser.uid)
        profiles.getOrPut(currentUser.uid) { MutableStateFlow(null) }.value = currentProfile.copy(
            firstUsStudentTaxYear = taxYear
        )
        return Result.success(Unit)
    }

    override suspend fun signOut() = Unit

    fun emitUser(user: FirebaseUser?) {
        authState.value = user
    }

    fun emitProfile(uid: String, profile: UserProfile?) {
        profiles.getOrPut(uid) { MutableStateFlow(null) }.value = profile
    }
}

class FakeDashboardRepository : DashboardRepository {
    private val employments = MutableStateFlow<List<Employment>>(emptyList())
    val addedEmployments = mutableListOf<Employment>()
    val deletedEmploymentIds = mutableListOf<String>()

    override fun getEmployments(uid: String): Flow<List<Employment>> = employments

    override suspend fun getEmploymentsSnapshot(uid: String): Result<List<Employment>> {
        return Result.success(employments.value)
    }

    override suspend fun addEmployment(uid: String, employment: Employment): Result<Unit> {
        addedEmployments += employment
        val current = employments.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == employment.id && employment.id.isNotBlank() }
        if (existingIndex >= 0) {
            current[existingIndex] = employment
        } else {
            current += employment
        }
        employments.value = current
        return Result.success(Unit)
    }

    override suspend fun deleteEmployment(uid: String, employmentId: String): Result<Unit> {
        deletedEmploymentIds += employmentId
        return Result.success(Unit)
    }

    override suspend fun getEmployment(uid: String, employmentId: String): Result<Employment?> {
        return Result.success(employments.value.firstOrNull { it.id == employmentId })
    }

    fun setEmployments(items: List<Employment>) {
        employments.value = items
    }
}

class FakeReportingRepository : ReportingRepository {
    private val obligations = MutableStateFlow<List<ReportingObligation>>(emptyList())
    private val wizards = MutableStateFlow<List<ReportingWizard>>(emptyList())
    val addedObligations = mutableListOf<ReportingObligation>()
    val toggledObligations = mutableListOf<Triple<String, String, Boolean>>()
    val deletedObligations = mutableListOf<String>()
    val updatedObligations = mutableListOf<ReportingObligation>()
    val startedWizards = mutableListOf<Triple<ReportingWizardEventType, String, Long>>()
    val seededObligationIds = mutableListOf<String>()
    val updatedWizardInputs = mutableListOf<Pair<String, ReportingWizardInput>>()
    val updatedWizardDrafts = mutableListOf<Pair<String, String>>()
    val copiedWizardIds = mutableListOf<String>()
    val completedWizardIds = mutableListOf<String>()
    var generatedDraftResult: Result<ReportingDraftResult> = Result.success(ReportingDraftResult())
    var toggleResult: Result<Unit> = Result.success(Unit)

    override fun getReportingObligations(uid: String): Flow<List<ReportingObligation>> = obligations
    override fun getReportingWizards(uid: String): Flow<List<ReportingWizard>> = wizards
    override fun getReportingWizard(uid: String, wizardId: String): Flow<ReportingWizard?> =
        wizards.map { items -> items.firstOrNull { it.id == wizardId } }

    override suspend fun addObligation(uid: String, obligation: ReportingObligation): Result<Unit> {
        addedObligations += obligation
        obligations.value = obligations.value + obligation
        return Result.success(Unit)
    }

    override suspend fun toggleObligationStatus(uid: String, obligationId: String, isCompleted: Boolean): Result<Unit> {
        toggledObligations += Triple(uid, obligationId, isCompleted)
        return toggleResult
    }

    override suspend fun deleteObligation(uid: String, obligationId: String): Result<Unit> {
        deletedObligations += obligationId
        return Result.success(Unit)
    }

    override suspend fun updateObligation(uid: String, obligation: ReportingObligation): Result<Unit> {
        updatedObligations += obligation
        return Result.success(Unit)
    }

    override suspend fun getObligation(uid: String, obligationId: String): Result<ReportingObligation?> {
        return Result.success(obligations.value.firstOrNull { it.id == obligationId })
    }

    override suspend fun startWizard(
        uid: String,
        eventType: ReportingWizardEventType,
        relatedEmploymentId: String,
        eventDate: Long
    ): Result<ReportingWizardStartResult> {
        startedWizards += Triple(eventType, relatedEmploymentId, eventDate)
        val wizardId = "wizard-${startedWizards.size}"
        val obligationId = "obligation-${startedWizards.size}"
        val wizard = ReportingWizard(
            id = wizardId,
            eventType = eventType.wireValue,
            relatedEmploymentId = relatedEmploymentId,
            eventDate = eventDate,
            dueDate = eventDate + 10,
            obligationId = obligationId
        )
        wizards.value = wizards.value + wizard
        obligations.value = obligations.value + ReportingObligation(
            id = obligationId,
            wizardId = wizardId,
            relatedEmploymentId = relatedEmploymentId,
            actionType = com.sidekick.opt_pal.data.model.ReportingActionType.OPEN_WIZARD.wireValue,
            sourceEventType = eventType.wireValue
        )
        return Result.success(ReportingWizardStartResult(wizardId, obligationId))
    }

    override suspend fun seedWizardFromObligation(uid: String, obligationId: String): Result<ReportingWizardStartResult> {
        seededObligationIds += obligationId
        val existingWizard = wizards.value.firstOrNull()
        return Result.success(
            ReportingWizardStartResult(
                wizardId = existingWizard?.id ?: "seeded-wizard",
                obligationId = obligationId
            )
        )
    }

    override suspend fun updateWizardUserInputs(
        uid: String,
        wizardId: String,
        userInputs: ReportingWizardInput
    ): Result<Unit> {
        updatedWizardInputs += wizardId to userInputs
        wizards.value = wizards.value.map { wizard ->
            if (wizard.id == wizardId) wizard.copy(userInputs = userInputs) else wizard
        }
        return Result.success(Unit)
    }

    override suspend fun updateWizardEditedDraft(uid: String, wizardId: String, editedDraft: String): Result<Unit> {
        updatedWizardDrafts += wizardId to editedDraft
        wizards.value = wizards.value.map { wizard ->
            if (wizard.id == wizardId) wizard.copy(editedDraft = editedDraft) else wizard
        }
        return Result.success(Unit)
    }

    override suspend fun markDraftCopied(uid: String, wizardId: String): Result<Unit> {
        copiedWizardIds += wizardId
        return Result.success(Unit)
    }

    override suspend fun completeWizard(uid: String, wizardId: String): Result<Unit> {
        completedWizardIds += wizardId
        wizards.value = wizards.value.map { wizard ->
            if (wizard.id == wizardId) {
                wizard.copy(status = com.sidekick.opt_pal.data.model.ReportingWizardStatus.COMPLETED.wireValue)
            } else {
                wizard
            }
        }
        return Result.success(Unit)
    }

    override suspend fun generateRelationshipDraft(
        wizardId: String,
        selectedDocumentIds: List<String>
    ): Result<ReportingDraftResult> {
        return generatedDraftResult
    }

    fun setObligations(items: List<ReportingObligation>) {
        obligations.value = items
    }

    fun setWizards(items: List<ReportingWizard>) {
        wizards.value = items
    }
}

class FakeDocumentRepository : DocumentRepository {
    private val documents = MutableStateFlow<List<DocumentMetadata>>(emptyList())
    var uploadResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    val uploadRequests = mutableListOf<UploadRequest>()
    val deleteRequests = mutableListOf<DocumentMetadata>()
    val renameRequests = mutableListOf<Pair<String, String>>()
    var progressEmissions: List<Pair<Long, Long>> = emptyList()
    var holdUploadCompletion: Boolean = false
    var uploadCompletion: CompletableDeferred<Unit>? = null
    var documentContentResult: Result<SecureDocumentContent> = Result.failure(IllegalStateException("No content stubbed"))

    override fun getDocuments(uid: String): Flow<List<DocumentMetadata>> = documents

    override suspend fun uploadDocument(
        uid: String,
        fileUri: Uri,
        fileName: String,
        userTag: String,
        consent: DocumentUploadConsent,
        documentCategory: DocumentCategory,
        chatEligible: Boolean?,
        contentResolver: ContentResolver,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
    ): Result<Unit> {
        uploadRequests += UploadRequest(
            uid = uid,
            fileName = fileName,
            userTag = userTag,
            processingMode = consent.processingMode,
            documentCategory = documentCategory,
            chatEligible = chatEligible
        )
        progressEmissions.forEach { (sent, total) -> onProgress(sent, total) }
        if (holdUploadCompletion) {
            val latch = CompletableDeferred<Unit>()
            uploadCompletion = latch
            latch.await()
            uploadCompletion = null
        }
        return uploadResult
    }

    override suspend fun deleteDocument(uid: String, document: DocumentMetadata): Result<Unit> {
        deleteRequests += document
        return deleteResult
    }

    override suspend fun renameDocument(uid: String, document: DocumentMetadata, newName: String): Result<Unit> {
        renameRequests += document.id to newName
        return Result.success(Unit)
    }

    override suspend fun getDocumentContent(document: DocumentMetadata): Result<SecureDocumentContent> {
        return documentContentResult
    }

    override suspend fun reprocessDocuments(): Result<String> {
        return Result.success("Reprocessing started")
    }

    fun setDocuments(items: List<DocumentMetadata>) {
        documents.value = items
    }

    data class UploadRequest(
        val uid: String,
        val fileName: String,
        val userTag: String,
        val processingMode: DocumentProcessingMode,
        val documentCategory: DocumentCategory,
        val chatEligible: Boolean?
    )
}

class FakeCaseStatusRepository : CaseStatusRepository {
    private val trackedCases = MutableStateFlow<List<UscisCaseTracker>>(emptyList())
    var trackerAvailabilityResult: Result<UscisTrackerAvailability> =
        Result.success(UscisTrackerAvailability(mode = "sandbox"))
    val trackedReceipts = mutableListOf<String>()
    val refreshedCaseIds = mutableListOf<String>()
    val archivedCaseIds = mutableListOf<String>()
    val removedCaseIds = mutableListOf<String>()
    val syncRequests = mutableListOf<Pair<Boolean, String?>>()
    var refreshResult: Result<UscisCaseRefreshResult> = Result.success(UscisCaseRefreshResult(refreshed = true))

    override fun observeTrackedCases(uid: String): Flow<List<UscisCaseTracker>> = trackedCases

    override suspend fun getTrackerAvailability(): Result<UscisTrackerAvailability> = trackerAvailabilityResult

    override suspend fun trackCase(receiptNumber: String): Result<String> {
        trackedReceipts += receiptNumber
        return Result.success(receiptNumber)
    }

    override suspend fun refreshCase(caseId: String): Result<UscisCaseRefreshResult> {
        refreshedCaseIds += caseId
        return refreshResult
    }

    override suspend fun archiveCase(caseId: String): Result<Unit> {
        archivedCaseIds += caseId
        return Result.success(Unit)
    }

    override suspend fun removeCase(caseId: String): Result<Unit> {
        removedCaseIds += caseId
        return Result.success(Unit)
    }

    override suspend fun handleMessagingTokenRefresh(token: String): Result<Unit> {
        syncRequests += true to token
        return Result.success(Unit)
    }

    override suspend fun syncMessagingEndpoint(enabled: Boolean, tokenOverride: String?): Result<Unit> {
        syncRequests += enabled to tokenOverride
        return Result.success(Unit)
    }

    fun setTrackedCases(items: List<UscisCaseTracker>) {
        trackedCases.value = items
    }
}

class FakeTravelAdvisorRepository : TravelAdvisorRepository {
    var entitlementResult: Result<TravelEntitlementState> = Result.success(
        TravelEntitlementState(
            isEnabled = true,
            source = TravelEntitlementSource.OPEN_BETA,
            message = "Enabled for tests."
        )
    )
    var refreshResult: Result<TravelPolicyBundle> = Result.failure(IllegalStateException("No policy bundle configured"))
    var cachedBundle: TravelPolicyBundle? = null

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<TravelEntitlementState> {
        return entitlementResult
    }

    override fun getCachedPolicyBundle(): TravelPolicyBundle? = cachedBundle

    override suspend fun refreshPolicyBundle(): Result<TravelPolicyBundle> {
        return refreshResult
    }
}

class FakeFicaRefundRepository : FicaRefundRepository {
    private val cases = MutableStateFlow<List<FicaRefundCase>>(emptyList())
    val createdCases = mutableListOf<W2ExtractionDraft>()
    val updatedInputs = mutableListOf<Pair<String, FicaUserTaxInputs>>()
    val updatedOutcomes = mutableListOf<Pair<String, EmployerRefundOutcome>>()
    val generatedEmployerPackets = mutableListOf<String>()
    val generatedIrsPackets = mutableListOf<Triple<String, String, String>>()
    var eligibilityResult: Result<FicaEligibilityResult> = Result.success(
        FicaEligibilityResult(classification = FicaEligibilityClassification.ELIGIBLE.wireValue)
    )
    var employerPacketResult: Result<FicaRefundPacket> = Result.success(
        FicaRefundPacket(documentId = "employer-packet", fileName = "Employer Packet.pdf", generatedAt = 1L, kind = "employer")
    )
    var irsPacketResult: Result<FicaRefundPacket> = Result.success(
        FicaRefundPacket(documentId = "irs-packet", fileName = "IRS Packet.pdf", generatedAt = 1L, kind = "irs")
    )

    override fun observeCases(uid: String): Flow<List<FicaRefundCase>> = cases

    override suspend fun createCase(uid: String, w2Document: W2ExtractionDraft): Result<String> {
        createdCases += w2Document
        val caseId = "fica-case-${createdCases.size}"
        cases.value = listOf(
            FicaRefundCase(
                id = caseId,
                w2DocumentId = w2Document.documentId,
                taxYear = w2Document.taxYear ?: 0,
                employerName = w2Document.employerName
            )
        ) + cases.value
        return Result.success(caseId)
    }

    override suspend fun updateUserInputs(uid: String, caseId: String, userInputs: FicaUserTaxInputs): Result<Unit> {
        updatedInputs += caseId to userInputs
        cases.value = cases.value.map { current ->
            if (current.id == caseId) current.copy(userInputs = userInputs) else current
        }
        return Result.success(Unit)
    }

    override suspend fun evaluateEligibility(caseId: String): Result<FicaEligibilityResult> {
        return eligibilityResult
    }

    override suspend fun updateEmployerOutcome(uid: String, caseId: String, outcome: EmployerRefundOutcome): Result<Unit> {
        updatedOutcomes += caseId to outcome
        cases.value = cases.value.map { current ->
            if (current.id == caseId) current.copy(employerOutcome = outcome.wireValue) else current
        }
        return Result.success(Unit)
    }

    override suspend fun generateEmployerPacket(caseId: String): Result<FicaRefundPacket> {
        generatedEmployerPackets += caseId
        return employerPacketResult
    }

    override suspend fun generateIrsPacket(
        caseId: String,
        fullSsn: String,
        fullEmployerEin: String,
        mailingAddress: String
    ): Result<FicaRefundPacket> {
        generatedIrsPackets += Triple(caseId, fullSsn, fullEmployerEin)
        return irsPacketResult
    }

    override suspend fun archiveCase(uid: String, caseId: String): Result<Unit> = Result.success(Unit)

    fun setCases(items: List<FicaRefundCase>) {
        cases.value = items
    }
}

class FakeUserSessionProvider(initialUserId: String? = "user-123") : UserSessionProvider {
    var userId: String? = initialUserId

    override val currentUserId: String?
        get() = userId
}
