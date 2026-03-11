package com.sidekick.opt_pal.testing.fakes

import android.content.ContentResolver
import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.core.calculations.utcStartOfDay
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.ComplianceHealthAvailability
import com.sidekick.opt_pal.data.model.ComplianceScoreSnapshot
import com.sidekick.opt_pal.data.model.ComplianceScoreSnapshotState
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
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bEmployerHistorySummary
import com.sidekick.opt_pal.data.model.H1bEmployerVerification
import com.sidekick.opt_pal.data.model.H1bEVerifyStatus
import com.sidekick.opt_pal.data.model.H1bEvidence
import com.sidekick.opt_pal.data.model.H1bProfile
import com.sidekick.opt_pal.data.model.H1bTimelineState
import com.sidekick.opt_pal.data.model.H1bCaseTracking
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983EntitlementSource
import com.sidekick.opt_pal.data.model.I983EntitlementState
import com.sidekick.opt_pal.data.model.I983ExportResult
import com.sidekick.opt_pal.data.model.I983NarrativeDraft
import com.sidekick.opt_pal.data.model.I983PolicyBundle
import com.sidekick.opt_pal.data.model.PolicyAlertAvailability
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertState
import com.sidekick.opt_pal.data.model.ReportingDraftResult
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingWizard
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.model.ReportingWizardInput
import com.sidekick.opt_pal.data.model.ReportingWizardStartResult
import com.sidekick.opt_pal.data.model.ScenarioEntitlementSource
import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.ScenarioSimulatorBundle
import com.sidekick.opt_pal.data.model.ScenarioSimulatorEntitlementState
import com.sidekick.opt_pal.data.model.TravelEntitlementSource
import com.sidekick.opt_pal.data.model.TravelEntitlementState
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import com.sidekick.opt_pal.data.model.UscisCaseRefreshResult
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UscisTrackedFormType
import com.sidekick.opt_pal.data.model.UscisTrackerAvailability
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayEntitlementSource
import com.sidekick.opt_pal.data.model.VisaPathwayEntitlementState
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import com.sidekick.opt_pal.data.model.W2ExtractionDraft
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.ComplianceHealthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.FicaRefundRepository
import com.sidekick.opt_pal.data.repository.H1bDashboardRepository
import com.sidekick.opt_pal.data.repository.I983AssistantRepository
import com.sidekick.opt_pal.data.repository.NotificationDeviceChannels
import com.sidekick.opt_pal.data.repository.NotificationDeviceRepository
import com.sidekick.opt_pal.data.repository.PolicyAlertRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.data.repository.ScenarioSimulatorRepository
import com.sidekick.opt_pal.data.repository.TravelAdvisorRepository
import com.sidekick.opt_pal.data.repository.VisaPathwayPlannerRepository
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

class FakeComplianceHealthRepository : ComplianceHealthRepository {
    var availabilityResult: Result<ComplianceHealthAvailability> = Result.success(
        ComplianceHealthAvailability(
            isEnabled = true,
            message = "Enabled for tests."
        )
    )
    val syncRequests = mutableListOf<Triple<String, Int, Long>>()
    private val currentSnapshots = mutableMapOf<String, ComplianceScoreSnapshot>()
    private val previousSnapshots = mutableMapOf<String, ComplianceScoreSnapshot>()

    override suspend fun resolveAvailability(): Result<ComplianceHealthAvailability> = availabilityResult

    override fun syncSnapshot(uid: String, score: Int, computedAt: Long): ComplianceScoreSnapshotState {
        syncRequests += Triple(uid, score, computedAt)
        val current = currentSnapshots[uid]
        val previous = previousSnapshots[uid]
        val nextCurrent = ComplianceScoreSnapshot(score = score, computedAt = computedAt)
        return if (current == null) {
            currentSnapshots[uid] = nextCurrent
            ComplianceScoreSnapshotState(current = nextCurrent, previous = null)
        } else if (utcStartOfDay(current.computedAt) == utcStartOfDay(computedAt)) {
            currentSnapshots[uid] = nextCurrent
            ComplianceScoreSnapshotState(current = nextCurrent, previous = previous)
        } else {
            previousSnapshots[uid] = current
            currentSnapshots[uid] = nextCurrent
            ComplianceScoreSnapshotState(current = nextCurrent, previous = current)
        }
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
        Result.success(
            UscisTrackerAvailability(
                mode = "sandbox",
                supportedForms = listOf(
                    UscisTrackedFormType.I765.wireValue,
                    UscisTrackedFormType.I129.wireValue
                )
            )
        )
    val trackedReceipts = mutableListOf<String>()
    val trackedFormHints = mutableListOf<UscisTrackedFormType?>()
    val refreshedCaseIds = mutableListOf<String>()
    val archivedCaseIds = mutableListOf<String>()
    val removedCaseIds = mutableListOf<String>()
    val syncRequests = mutableListOf<Pair<Boolean, String?>>()
    var refreshResult: Result<UscisCaseRefreshResult> = Result.success(UscisCaseRefreshResult(refreshed = true))

    override fun observeTrackedCases(uid: String): Flow<List<UscisCaseTracker>> = trackedCases

    override suspend fun getTrackerAvailability(): Result<UscisTrackerAvailability> = trackerAvailabilityResult

    override suspend fun trackCase(
        receiptNumber: String,
        expectedFormType: UscisTrackedFormType?
    ): Result<String> {
        trackedReceipts += receiptNumber
        trackedFormHints += expectedFormType
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

class FakeNotificationDeviceRepository : NotificationDeviceRepository {
    var caseStatusEnabled: Boolean = false
    var policyAlertsEnabled: Boolean = false
    val syncRequests = mutableListOf<Triple<Boolean?, Boolean?, String?>>()
    val tokenRefreshes = mutableListOf<String>()

    override suspend fun handleTokenRefresh(token: String): Result<Unit> {
        tokenRefreshes += token
        return Result.success(Unit)
    }

    override suspend fun syncChannels(
        caseStatusEnabled: Boolean?,
        policyAlertsEnabled: Boolean?,
        tokenOverride: String?
    ): Result<NotificationDeviceChannels> {
        syncRequests += Triple(caseStatusEnabled, policyAlertsEnabled, tokenOverride)
        caseStatusEnabled?.let { this.caseStatusEnabled = it }
        policyAlertsEnabled?.let { this.policyAlertsEnabled = it }
        return Result.success(
            NotificationDeviceChannels(
                caseStatusEnabled = this.caseStatusEnabled,
                policyAlertsEnabled = this.policyAlertsEnabled
            )
        )
    }

    override fun isCaseStatusEnabled(): Boolean = caseStatusEnabled

    override fun isPolicyAlertsEnabled(): Boolean = policyAlertsEnabled
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

class FakeI983AssistantRepository : I983AssistantRepository {
    private val drafts = mutableMapOf<String, MutableStateFlow<List<I983Draft>>>()
    var entitlementResult: Result<I983EntitlementState> = Result.success(
        I983EntitlementState(
            isEnabled = true,
            source = I983EntitlementSource.OPEN_BETA,
            message = "Enabled for tests."
        )
    )
    var cachedBundle: I983PolicyBundle? = null
    var refreshResult: Result<I983PolicyBundle> = Result.failure(IllegalStateException("No I-983 bundle configured"))

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<I983EntitlementState> = entitlementResult

    override fun getCachedPolicyBundle(): I983PolicyBundle? = cachedBundle

    override suspend fun refreshPolicyBundle(): Result<I983PolicyBundle> = refreshResult

    override fun observeDrafts(uid: String): Flow<List<I983Draft>> = drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }

    override fun observeDraft(uid: String, draftId: String): Flow<I983Draft?> =
        drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.map { items -> items.firstOrNull { it.id == draftId } }

    override suspend fun createDraft(uid: String, draft: I983Draft): Result<String> {
        val nextId = draft.id.ifBlank { "draft-${drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value.size + 1}" }
        val nextDraft = draft.copy(id = nextId)
        drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value =
            drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value + nextDraft
        return Result.success(nextId)
    }

    override suspend fun updateDraft(uid: String, draft: I983Draft): Result<Unit> {
        val current = drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value.toMutableList()
        val index = current.indexOfFirst { it.id == draft.id }
        if (index >= 0) current[index] = draft else current += draft
        drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value = current
        return Result.success(Unit)
    }

    override suspend fun generateSectionDrafts(draftId: String, selectedDocumentIds: List<String>): Result<I983NarrativeDraft> {
        return Result.success(I983NarrativeDraft())
    }

    override suspend fun exportOfficialPdf(draftId: String): Result<I983ExportResult> {
        return Result.success(I983ExportResult(documentId = "doc-1", fileName = "i983.pdf", generatedAt = 1L, templateVersion = "test"))
    }

    override suspend fun linkSignedDocument(uid: String, draftId: String, signedDocumentId: String): Result<Unit> {
        return Result.success(Unit)
    }

    fun setDrafts(uid: String, items: List<I983Draft>) {
        drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value = items
    }
}

class FakePolicyAlertRepository : PolicyAlertRepository {
    private val alerts = MutableStateFlow<List<PolicyAlertCard>>(emptyList())
    private val states = mutableMapOf<String, MutableStateFlow<List<PolicyAlertState>>>()
    var availabilityResult: Result<PolicyAlertAvailability> = Result.success(
        PolicyAlertAvailability(
            isEnabled = true,
            message = "Enabled for tests."
        )
    )
    var notificationsEnabled: Boolean = false
    val openedAlertIds = mutableListOf<Pair<String, String>>()
    val syncRequests = mutableListOf<Pair<Boolean, String?>>()

    override suspend fun resolveAvailability(): Result<PolicyAlertAvailability> = availabilityResult

    override fun observePublishedAlerts() = alerts

    override fun observeAlertStates(uid: String) =
        states.getOrPut(uid) { MutableStateFlow(emptyList()) }

    override suspend fun markAlertOpened(uid: String, alertId: String): Result<Unit> {
        openedAlertIds += uid to alertId
        val current = states.getOrPut(uid) { MutableStateFlow(emptyList()) }.value.toMutableList()
        val next = PolicyAlertState(id = alertId, alertId = alertId, openedAt = 1L, lastSeenAt = 1L)
        val index = current.indexOfFirst { it.alertId == alertId }
        if (index >= 0) {
            current[index] = next
        } else {
            current += next
        }
        states.getOrPut(uid) { MutableStateFlow(emptyList()) }.value = current
        return Result.success(Unit)
    }

    override suspend fun syncNotificationPreference(enabled: Boolean, tokenOverride: String?): Result<Unit> {
        notificationsEnabled = enabled
        syncRequests += enabled to tokenOverride
        return Result.success(Unit)
    }

    override fun isNotificationPreferenceEnabled(): Boolean = notificationsEnabled

    fun setAlerts(items: List<PolicyAlertCard>) {
        alerts.value = items
    }

    fun setStates(uid: String, items: List<PolicyAlertState>) {
        states.getOrPut(uid) { MutableStateFlow(emptyList()) }.value = items
    }
}

class FakeVisaPathwayPlannerRepository : VisaPathwayPlannerRepository {
    private val profiles = mutableMapOf<String, MutableStateFlow<VisaPathwayProfile?>>()
    var entitlementResult: Result<VisaPathwayEntitlementState> = Result.success(
        VisaPathwayEntitlementState(
            isEnabled = true,
            source = VisaPathwayEntitlementSource.OPEN_BETA,
            message = "Enabled for tests."
        )
    )
    var refreshResult: Result<VisaPathwayPlannerBundle> = Result.failure(IllegalStateException("No planner bundle configured"))
    var cachedPlannerBundle: VisaPathwayPlannerBundle? = null
    val savedProfiles = mutableListOf<Pair<String, VisaPathwayProfile>>()

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<VisaPathwayEntitlementState> {
        return entitlementResult
    }

    override fun observeProfile(uid: String): Flow<VisaPathwayProfile?> {
        return profiles.getOrPut(uid) { MutableStateFlow(null) }
    }

    override suspend fun saveProfile(uid: String, profile: VisaPathwayProfile): Result<Unit> {
        savedProfiles += uid to profile
        profiles.getOrPut(uid) { MutableStateFlow(null) }.value = profile
        return Result.success(Unit)
    }

    override fun getCachedBundle(): VisaPathwayPlannerBundle? = cachedPlannerBundle

    override suspend fun refreshBundle(): Result<VisaPathwayPlannerBundle> = refreshResult

    fun setProfile(uid: String, profile: VisaPathwayProfile?) {
        profiles.getOrPut(uid) { MutableStateFlow(null) }.value = profile
    }
}

class FakeH1bDashboardRepository : H1bDashboardRepository {
    private val profiles = mutableMapOf<String, MutableStateFlow<H1bProfile?>>()
    private val employerVerifications = mutableMapOf<String, MutableStateFlow<H1bEmployerVerification?>>()
    private val timelineStates = mutableMapOf<String, MutableStateFlow<H1bTimelineState?>>()
    private val caseTrackingStates = mutableMapOf<String, MutableStateFlow<H1bCaseTracking?>>()
    private val evidenceStates = mutableMapOf<String, MutableStateFlow<H1bEvidence?>>()
    var refreshResult: Result<H1bDashboardBundle> =
        Result.failure(IllegalStateException("No H-1B dashboard bundle configured"))
    var cachedDashboardBundle: H1bDashboardBundle? = null
    var employerHistoryResult: Result<H1bEmployerHistorySummary> =
        Result.failure(IllegalStateException("No employer history stub configured"))
    var eVerifySnapshotResult: Result<H1bEmployerVerification> =
        Result.success(H1bEmployerVerification(eVerifyStatus = H1bEVerifyStatus.UNKNOWN.wireValue))
    val savedProfiles = mutableListOf<Pair<String, H1bProfile>>()
    val savedEmployerVerifications = mutableListOf<Pair<String, H1bEmployerVerification>>()
    val savedTimelineStates = mutableListOf<Pair<String, H1bTimelineState>>()
    val savedCaseTrackingStates = mutableListOf<Pair<String, H1bCaseTracking>>()
    val savedEvidence = mutableListOf<Pair<String, H1bEvidence>>()
    val employerHistorySearches = mutableListOf<Triple<String, String?, String?>>()
    val eVerifySnapshotRequests = mutableListOf<Quadruple<String, String?, String?, H1bEVerifyStatus>>()

    override fun observeProfile(uid: String): Flow<H1bProfile?> =
        profiles.getOrPut(uid) { MutableStateFlow(null) }

    override fun observeEmployerVerification(uid: String): Flow<H1bEmployerVerification?> =
        employerVerifications.getOrPut(uid) { MutableStateFlow(null) }

    override fun observeTimelineState(uid: String): Flow<H1bTimelineState?> =
        timelineStates.getOrPut(uid) { MutableStateFlow(null) }

    override fun observeCaseTracking(uid: String): Flow<H1bCaseTracking?> =
        caseTrackingStates.getOrPut(uid) { MutableStateFlow(null) }

    override fun observeEvidence(uid: String): Flow<H1bEvidence?> =
        evidenceStates.getOrPut(uid) { MutableStateFlow(null) }

    override suspend fun saveProfile(uid: String, profile: H1bProfile): Result<Unit> {
        savedProfiles += uid to profile
        profiles.getOrPut(uid) { MutableStateFlow(null) }.value = profile
        return Result.success(Unit)
    }

    override suspend fun saveEmployerVerification(uid: String, verification: H1bEmployerVerification): Result<Unit> {
        savedEmployerVerifications += uid to verification
        employerVerifications.getOrPut(uid) { MutableStateFlow(null) }.value = verification
        return Result.success(Unit)
    }

    override suspend fun saveTimelineState(uid: String, timelineState: H1bTimelineState): Result<Unit> {
        savedTimelineStates += uid to timelineState
        timelineStates.getOrPut(uid) { MutableStateFlow(null) }.value = timelineState
        return Result.success(Unit)
    }

    override suspend fun saveCaseTracking(uid: String, caseTracking: H1bCaseTracking): Result<Unit> {
        savedCaseTrackingStates += uid to caseTracking
        caseTrackingStates.getOrPut(uid) { MutableStateFlow(null) }.value = caseTracking
        return Result.success(Unit)
    }

    override suspend fun saveEvidence(uid: String, evidence: H1bEvidence): Result<Unit> {
        savedEvidence += uid to evidence
        evidenceStates.getOrPut(uid) { MutableStateFlow(null) }.value = evidence
        return Result.success(Unit)
    }

    override fun getCachedBundle(): H1bDashboardBundle? = cachedDashboardBundle

    override suspend fun refreshBundle(): Result<H1bDashboardBundle> = refreshResult

    override suspend fun searchEmployerHistory(
        employerName: String,
        employerCity: String?,
        employerState: String?
    ): Result<H1bEmployerHistorySummary> {
        employerHistorySearches += Triple(employerName, employerCity, employerState)
        return employerHistoryResult
    }

    override suspend fun saveEVerifySnapshot(
        employerName: String,
        employerCity: String?,
        employerState: String?,
        status: H1bEVerifyStatus
    ): Result<H1bEmployerVerification> {
        eVerifySnapshotRequests += Quadruple(employerName, employerCity, employerState, status)
        return eVerifySnapshotResult
    }

    fun setProfile(uid: String, profile: H1bProfile?) {
        profiles.getOrPut(uid) { MutableStateFlow(null) }.value = profile
    }

    fun setEmployerVerification(uid: String, verification: H1bEmployerVerification?) {
        employerVerifications.getOrPut(uid) { MutableStateFlow(null) }.value = verification
    }

    fun setTimelineState(uid: String, timelineState: H1bTimelineState?) {
        timelineStates.getOrPut(uid) { MutableStateFlow(null) }.value = timelineState
    }

    fun setCaseTracking(uid: String, caseTracking: H1bCaseTracking?) {
        caseTrackingStates.getOrPut(uid) { MutableStateFlow(null) }.value = caseTracking
    }

    fun setEvidence(uid: String, evidence: H1bEvidence?) {
        evidenceStates.getOrPut(uid) { MutableStateFlow(null) }.value = evidence
    }
}

class FakeScenarioSimulatorRepository : ScenarioSimulatorRepository {
    private val drafts = mutableMapOf<String, MutableStateFlow<List<ScenarioDraft>>>()
    var entitlementResult: Result<ScenarioSimulatorEntitlementState> = Result.success(
        ScenarioSimulatorEntitlementState(
            isEnabled = true,
            source = ScenarioEntitlementSource.OPEN_BETA,
            message = "Enabled for tests."
        )
    )
    var cachedScenarioBundle: ScenarioSimulatorBundle? = null
    var refreshResult: Result<ScenarioSimulatorBundle> =
        Result.failure(IllegalStateException("No scenario simulator bundle configured"))
    val savedDrafts = mutableListOf<Pair<String, ScenarioDraft>>()
    val deletedDrafts = mutableListOf<Pair<String, String>>()

    override suspend fun resolveEntitlement(userFlag: Boolean?): Result<ScenarioSimulatorEntitlementState> {
        return entitlementResult
    }

    override fun observeDrafts(uid: String): Flow<List<ScenarioDraft>> =
        drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }

    override suspend fun saveDraft(uid: String, draft: ScenarioDraft): Result<String> {
        savedDrafts += uid to draft
        val nextId = draft.id.ifBlank { "scenario-${drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value.size + 1}" }
        val nextDraft = draft.copy(id = nextId)
        val current = drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value.toMutableList()
        val index = current.indexOfFirst { it.id == nextId }
        if (index >= 0) {
            current[index] = nextDraft
        } else {
            current += nextDraft
        }
        drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value = current
        return Result.success(nextId)
    }

    override suspend fun deleteDraft(uid: String, scenarioId: String): Result<Unit> {
        deletedDrafts += uid to scenarioId
        drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value =
            drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value.filterNot { it.id == scenarioId }
        return Result.success(Unit)
    }

    override fun getCachedBundle(): ScenarioSimulatorBundle? = cachedScenarioBundle

    override suspend fun refreshBundle(): Result<ScenarioSimulatorBundle> = refreshResult

    fun setDrafts(uid: String, items: List<ScenarioDraft>) {
        drafts.getOrPut(uid) { MutableStateFlow(emptyList()) }.value = items
    }
}

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

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
