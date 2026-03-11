package com.sidekick.opt_pal.feature.scenario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.scenario.ScenarioBaselineSnapshot
import com.sidekick.opt_pal.core.scenario.ScenarioSimulatorEngine
import com.sidekick.opt_pal.data.model.EmployerChangeScenarioAssumptions
import com.sidekick.opt_pal.data.model.JobLossScenarioAssumptions
import com.sidekick.opt_pal.data.model.PendingStemExtensionScenarioAssumptions
import com.sidekick.opt_pal.data.model.ReportingDeadlineScenarioAssumptions
import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.ScenarioDraftOutcomeSummary
import com.sidekick.opt_pal.data.model.ScenarioOutcome
import com.sidekick.opt_pal.data.model.ScenarioSimulationResult
import com.sidekick.opt_pal.data.model.ScenarioSimulatorBundle
import com.sidekick.opt_pal.data.model.ScenarioSimulatorEntitlementState
import com.sidekick.opt_pal.data.model.ScenarioTemplateId
import com.sidekick.opt_pal.data.model.TravelScenarioAssumptions
import com.sidekick.opt_pal.data.model.TravelTripInput
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import com.sidekick.opt_pal.data.model.defaultDraftName
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.H1bDashboardRepository
import com.sidekick.opt_pal.data.repository.I983AssistantRepository
import com.sidekick.opt_pal.data.repository.PolicyAlertRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.data.repository.ScenarioSimulatorRepository
import com.sidekick.opt_pal.data.repository.TravelAdvisorRepository
import com.sidekick.opt_pal.data.repository.VisaPathwayPlannerRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ScenarioSimulatorUiState(
    val isLoading: Boolean = true,
    val isRefreshingBundles: Boolean = false,
    val isSavingDraft: Boolean = false,
    val isDeletingDraft: Boolean = false,
    val entitlement: ScenarioSimulatorEntitlementState = ScenarioSimulatorEntitlementState(),
    val bundle: ScenarioSimulatorBundle? = null,
    val drafts: List<ScenarioDraft> = emptyList(),
    val workingDraft: ScenarioDraft? = null,
    val currentResult: ScenarioSimulationResult? = null,
    val baselineChanged: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val activeTemplateId: ScenarioTemplateId?
        get() = workingDraft?.parsedTemplateId
}

class ScenarioSimulatorViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val reportingRepository: ReportingRepository,
    private val documentRepository: DocumentRepository,
    private val i983AssistantRepository: I983AssistantRepository,
    private val caseStatusRepository: CaseStatusRepository,
    private val plannerRepository: VisaPathwayPlannerRepository,
    private val h1bDashboardRepository: H1bDashboardRepository,
    private val travelAdvisorRepository: TravelAdvisorRepository,
    private val policyAlertRepository: PolicyAlertRepository,
    private val scenarioRepository: ScenarioSimulatorRepository,
    private val engine: ScenarioSimulatorEngine = ScenarioSimulatorEngine(),
    private val initialTemplateId: String?,
    private val initialDraftId: String?,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScenarioSimulatorUiState())
    val uiState = _uiState.asStateFlow()

    private val entitlementState = MutableStateFlow(ScenarioSimulatorEntitlementState())
    private val scenarioBundleState = MutableStateFlow<ScenarioSimulatorBundle?>(null)
    private val travelBundleState = MutableStateFlow<com.sidekick.opt_pal.data.model.TravelPolicyBundle?>(null)
    private val pathwayBundleState = MutableStateFlow<com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle?>(null)
    private val h1bBundleState = MutableStateFlow<com.sidekick.opt_pal.data.model.H1bDashboardBundle?>(null)

    private var currentUid: String? = null
    private var currentBaseline: ScenarioBaselineSnapshot? = null
    private var observationJob: Job? = null
    private var lastEntitlementFlag: Boolean? = null
    private var hasLocalChanges = false

    init {
        authRepository.getAuthState()
            .onEach { user ->
                currentUid = user?.uid
                lastEntitlementFlag = null
                observationJob?.cancel()
                hasLocalChanges = false
                if (user == null) {
                    _uiState.value = ScenarioSimulatorUiState(
                        isLoading = false,
                        errorMessage = "User not logged in."
                    )
                } else {
                    loadBundles()
                    observeUserData(user.uid)
                }
            }
            .launchIn(viewModelScope)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun refreshBundles() {
        loadBundles(forceInfoMessage = true)
    }

    fun selectTemplate(templateId: ScenarioTemplateId) {
        hasLocalChanges = false
        val now = timeProvider()
        _uiState.value = _uiState.value.copy(
            workingDraft = ScenarioDraft(
                templateId = templateId.wireValue,
                name = templateId.defaultDraftName(),
                assumptions = defaultAssumptionsFor(templateId),
                createdAt = now,
                updatedAt = now
            ),
            currentResult = null,
            baselineChanged = false,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun selectDraft(draftId: String) {
        hasLocalChanges = false
        val draft = _uiState.value.drafts.firstOrNull { it.id == draftId } ?: return
        _uiState.value = _uiState.value.copy(
            workingDraft = draft,
            currentResult = null,
            baselineChanged = currentBaseline?.fingerprint()?.value != draft.baselineFingerprint.value,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun updateDraftName(value: String) {
        updateWorkingDraft { copy(name = value) }
    }

    fun togglePinned() {
        updateWorkingDraft { copy(pinned = !pinned) }
    }

    fun archiveCurrentDraft() {
        updateWorkingDraft {
            copy(archivedAt = if (archivedAt == null) timeProvider() else null)
        }
    }

    fun deleteCurrentDraft() {
        val uid = currentUid ?: return
        val draftId = _uiState.value.workingDraft?.id?.takeIf { it.isNotBlank() } ?: return
        _uiState.value = _uiState.value.copy(isDeletingDraft = true, errorMessage = null)
        viewModelScope.launch {
            scenarioRepository.deleteDraft(uid, draftId)
                .onSuccess {
                    hasLocalChanges = false
                    _uiState.value = _uiState.value.copy(
                        isDeletingDraft = false,
                        workingDraft = null,
                        currentResult = null,
                        infoMessage = "Scenario draft deleted."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDeletingDraft = false,
                        errorMessage = error.message ?: "Unable to delete the scenario draft."
                    )
                }
        }
    }

    fun saveCurrentDraft() {
        val uid = currentUid ?: return
        val baseline = currentBaseline ?: return
        val draft = _uiState.value.workingDraft ?: return
        _uiState.value = _uiState.value.copy(isSavingDraft = true, errorMessage = null)
        viewModelScope.launch {
            val resultSummary = _uiState.value.currentResult?.let { simulation ->
                ScenarioDraftOutcomeSummary(
                    outcome = simulation.outcome.name,
                    headline = simulation.headline,
                    confidence = simulation.confidence.name,
                    computedAt = simulation.computedAt
                )
            }
            val persisted = draft.copy(
                lastRunAt = _uiState.value.currentResult?.computedAt ?: draft.lastRunAt,
                baselineFingerprint = baseline.fingerprint(),
                lastOutcome = resultSummary
            )
            scenarioRepository.saveDraft(uid, persisted)
                .onSuccess { draftId ->
                    hasLocalChanges = false
                    _uiState.value = _uiState.value.copy(
                        isSavingDraft = false,
                        workingDraft = persisted.copy(id = draftId),
                        baselineChanged = false,
                        infoMessage = "Scenario draft saved."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSavingDraft = false,
                        errorMessage = error.message ?: "Unable to save the scenario draft."
                    )
                }
        }
    }

    fun runSimulation() {
        val draft = _uiState.value.workingDraft ?: return
        val baseline = currentBaseline ?: return
        val result = engine.simulate(draft, baseline, timeProvider())
        hasLocalChanges = true
        _uiState.value = _uiState.value.copy(
            currentResult = result,
            workingDraft = draft.copy(
                lastRunAt = result.computedAt,
                lastOutcome = ScenarioDraftOutcomeSummary(
                    outcome = result.outcome.name,
                    headline = result.headline,
                    confidence = result.confidence.name,
                    computedAt = result.computedAt
                )
            ),
            baselineChanged = baseline.fingerprint().value != draft.baselineFingerprint.value,
            infoMessage = "Scenario rerun against the current baseline."
        )
    }

    fun updateJobLossStartDate(value: Long?) {
        updateAssumptions<JobLossScenarioAssumptions> { copy(interruptionStartDate = value) }
    }

    fun updateJobLossReplacementStartDate(value: Long?) {
        updateAssumptions<JobLossScenarioAssumptions> { copy(replacementStartDate = value) }
    }

    fun updateJobLossReplacementEmployer(value: String) {
        updateAssumptions<JobLossScenarioAssumptions> { copy(replacementEmployerName = value) }
    }

    fun updateJobLossReplacementHours(value: String) {
        updateAssumptions<JobLossScenarioAssumptions> { copy(replacementHoursPerWeek = value.toIntOrNull()) }
    }

    fun updateJobLossUnauthorizedEmployment(value: Boolean?) {
        updateAssumptions<JobLossScenarioAssumptions> { copy(hasUnauthorizedEmployment = value) }
    }

    fun updateJobLossStatusViolation(value: Boolean?) {
        updateAssumptions<JobLossScenarioAssumptions> { copy(hasStatusViolation = value) }
    }

    fun updateEmployerChangeDate(value: Long?) {
        updateAssumptions<EmployerChangeScenarioAssumptions> { copy(changeDate = value) }
    }

    fun updateEmployerChangeName(value: String) {
        updateAssumptions<EmployerChangeScenarioAssumptions> { copy(newEmployerName = value) }
    }

    fun updateEmployerChangeHours(value: String) {
        updateAssumptions<EmployerChangeScenarioAssumptions> { copy(newEmployerHoursPerWeek = value.toIntOrNull()) }
    }

    fun updateEmployerChangeUsesEVerify(value: Boolean?) {
        updateAssumptions<EmployerChangeScenarioAssumptions> { copy(newEmployerUsesEVerify = value) }
    }

    fun updateEmployerChangeRoleRelation(value: Boolean?) {
        updateAssumptions<EmployerChangeScenarioAssumptions> { copy(roleRelatedToDegree = value) }
    }

    fun updateEmployerChangeHasI983(value: Boolean?) {
        updateAssumptions<EmployerChangeScenarioAssumptions> { copy(hasNewI983 = value) }
    }

    fun updateEmployerChangeHasI20(value: Boolean?) {
        updateAssumptions<EmployerChangeScenarioAssumptions> { copy(hasNewI20 = value) }
    }

    fun updateReportingLabel(value: String) {
        updateAssumptions<ReportingDeadlineScenarioAssumptions> { copy(reportingLabel = value) }
    }

    fun updateReportingDueDate(value: Long?) {
        updateAssumptions<ReportingDeadlineScenarioAssumptions> { copy(dueDate = value) }
    }

    fun updateReportingSubmittedLate(value: Boolean?) {
        updateAssumptions<ReportingDeadlineScenarioAssumptions> { copy(submittedLate = value) }
    }

    fun updateReportingStemValidation(value: Boolean?) {
        updateAssumptions<ReportingDeadlineScenarioAssumptions> { copy(isStemValidation = value) }
    }

    fun updateReportingFinalEvaluation(value: Boolean?) {
        updateAssumptions<ReportingDeadlineScenarioAssumptions> { copy(isFinalEvaluation = value) }
    }

    fun updateTravelTripInput(transform: TravelTripInput.() -> TravelTripInput) {
        updateAssumptions<TravelScenarioAssumptions> { copy(tripInput = tripInput.transform()) }
    }

    fun updateH1bWorkflowStage(value: String) {
        updateAssumptions<com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions> { copy(workflowStage = value) }
    }

    fun updateH1bSelectedRegistration(value: Boolean?) {
        updateAssumptions<com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions> { copy(selectedRegistration = value) }
    }

    fun updateH1bFiledPetition(value: Boolean?) {
        updateAssumptions<com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions> { copy(filedPetition = value) }
    }

    fun updateH1bRequestedCos(value: Boolean?) {
        updateAssumptions<com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions> { copy(requestedChangeOfStatus = value) }
    }

    fun updateH1bRequestedConsular(value: Boolean?) {
        updateAssumptions<com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions> { copy(requestedConsularProcessing = value) }
    }

    fun updateH1bTravelPlanned(value: Boolean?) {
        updateAssumptions<com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions> { copy(travelPlanned = value) }
    }

    fun updateH1bReceiptNotice(value: Boolean?) {
        updateAssumptions<com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions> { copy(hasReceiptNotice = value) }
    }

    fun updatePendingStemFilingDate(value: Long?) {
        updateAssumptions<PendingStemExtensionScenarioAssumptions> { copy(filingDate = value) }
    }

    fun updatePendingStemOptEndDate(value: Long?) {
        updateAssumptions<PendingStemExtensionScenarioAssumptions> { copy(optEndDate = value) }
    }

    fun updatePendingStemReceiptNotice(value: Boolean?) {
        updateAssumptions<PendingStemExtensionScenarioAssumptions> { copy(hasReceiptNotice = value) }
    }

    fun updatePendingStemTravelPlanned(value: Boolean?) {
        updateAssumptions<PendingStemExtensionScenarioAssumptions> { copy(travelPlanned = value) }
    }

    fun updatePendingStemEmployerChange(value: Boolean?) {
        updateAssumptions<PendingStemExtensionScenarioAssumptions> { copy(employerChangePlanned = value) }
    }

    fun updatePendingStemHasI983(value: Boolean?) {
        updateAssumptions<PendingStemExtensionScenarioAssumptions> { copy(hasNewI983 = value) }
    }

    fun updatePendingStemHasI20(value: Boolean?) {
        updateAssumptions<PendingStemExtensionScenarioAssumptions> { copy(hasNewI20 = value) }
    }

    private fun observeUserData(uid: String) {
        val primary = combine(
            authRepository.getUserProfile(uid),
            dashboardRepository.getEmployments(uid),
            reportingRepository.getReportingObligations(uid),
            documentRepository.getDocuments(uid),
            scenarioRepository.observeDrafts(uid)
        ) { profile, employments, reporting, documents, drafts ->
            ScenarioPrimaryObserved(profile, employments, reporting, documents, drafts)
        }
        val secondary = combine(
            i983AssistantRepository.observeDrafts(uid),
            caseStatusRepository.observeTrackedCases(uid),
            plannerRepository.observeProfile(uid),
            h1bDashboardRepository.observeProfile(uid),
            h1bDashboardRepository.observeEmployerVerification(uid)
        ) { i983Drafts, trackedCases, plannerProfile, h1bProfile, employerVerification ->
            ScenarioSecondaryObserved(i983Drafts, trackedCases, plannerProfile, h1bProfile, employerVerification)
        }
        val tertiary = combine(
            h1bDashboardRepository.observeTimelineState(uid),
            h1bDashboardRepository.observeCaseTracking(uid),
            h1bDashboardRepository.observeEvidence(uid),
            policyAlertRepository.observePublishedAlerts(),
            policyAlertRepository.observeAlertStates(uid)
        ) { timelineState, caseTracking, evidence, policyAlerts, policyStates ->
            ScenarioTertiaryObserved(timelineState, caseTracking, evidence, policyAlerts, policyStates)
        }
        val bundles = combine(
            scenarioBundleState,
            travelBundleState,
            pathwayBundleState,
            h1bBundleState
        ) { scenarioBundle, travelBundle, pathwayBundle, h1bBundle ->
            ScenarioBundleObserved(scenarioBundle, travelBundle, pathwayBundle, h1bBundle)
        }
        observationJob = combine(
            primary,
            secondary,
            tertiary,
            bundles,
            entitlementState
        ) { primaryObserved, secondaryObserved, tertiaryObserved, bundleObserved, entitlement ->
            ScenarioObserved(
                primary = primaryObserved,
                secondary = secondaryObserved,
                tertiary = tertiaryObserved,
                bundles = bundleObserved,
                entitlement = entitlement
            )
        }.onEach { observed ->
            maybeResolveEntitlement(observed.primary.profile?.scenarioSimulatorEnabled)
            val baseline = ScenarioBaselineSnapshot(
                userProfile = observed.primary.profile,
                employments = observed.primary.employments,
                reportingObligations = observed.primary.reporting,
                documents = observed.primary.documents,
                i983Drafts = observed.secondary.i983Drafts,
                trackedCases = observed.secondary.trackedCases,
                plannerProfile = observed.secondary.plannerProfile,
                h1bProfile = observed.secondary.h1bProfile,
                h1bEmployerVerification = observed.secondary.employerVerification,
                h1bTimelineState = observed.tertiary.timelineState,
                h1bCaseTracking = observed.tertiary.caseTracking,
                h1bEvidence = observed.tertiary.evidence,
                policyAlerts = observed.tertiary.policyAlerts,
                policyStates = observed.tertiary.policyStates,
                travelBundle = observed.bundles.travelBundle,
                pathwayBundle = observed.bundles.pathwayBundle,
                h1bBundle = observed.bundles.h1bBundle,
                scenarioBundle = observed.bundles.scenarioBundle
            )
            currentBaseline = baseline
            val nextWorkingDraft = resolveWorkingDraft(
                drafts = observed.primary.drafts,
                baseline = baseline,
                current = _uiState.value.workingDraft
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                entitlement = observed.entitlement,
                bundle = observed.bundles.scenarioBundle,
                drafts = observed.primary.drafts,
                workingDraft = nextWorkingDraft,
                baselineChanged = nextWorkingDraft?.baselineFingerprint?.value
                    ?.takeIf { it.isNotBlank() }
                    ?.let { it != baseline.fingerprint().value } == true
            )
        }.launchIn(viewModelScope)
    }

    private fun maybeResolveEntitlement(userFlag: Boolean?) {
        if (userFlag == lastEntitlementFlag) return
        lastEntitlementFlag = userFlag
        viewModelScope.launch {
            scenarioRepository.resolveEntitlement(userFlag)
                .onSuccess { entitlementState.value = it }
                .onFailure { error ->
                    entitlementState.value = ScenarioSimulatorEntitlementState(
                        isEnabled = false,
                        message = error.message ?: "Unable to determine Scenario Simulator access."
                    )
                }
        }
    }

    private fun resolveWorkingDraft(
        drafts: List<ScenarioDraft>,
        baseline: ScenarioBaselineSnapshot,
        current: ScenarioDraft?
    ): ScenarioDraft? {
        if (hasLocalChanges && current != null) return current
        current?.id?.takeIf { it.isNotBlank() }?.let { currentId ->
            drafts.firstOrNull { it.id == currentId }?.let { return it }
        }
        initialDraftId?.let { draftId ->
            drafts.firstOrNull { it.id == draftId }?.let { return it }
        }
        initialTemplateId?.let { template ->
            return ScenarioDraft(
                templateId = ScenarioTemplateId.fromWireValue(template).wireValue,
                name = ScenarioTemplateId.fromWireValue(template).defaultDraftName(),
                assumptions = defaultAssumptionsFor(ScenarioTemplateId.fromWireValue(template)),
                createdAt = timeProvider(),
                updatedAt = timeProvider(),
                baselineFingerprint = baseline.fingerprint()
            )
        }
        return drafts.firstOrNull { !it.isArchived } ?: current
    }

    private fun loadBundles(forceInfoMessage: Boolean = false) {
        scenarioBundleState.value = scenarioRepository.getCachedBundle()
        travelBundleState.value = travelAdvisorRepository.getCachedPolicyBundle()
        pathwayBundleState.value = plannerRepository.getCachedBundle()
        h1bBundleState.value = h1bDashboardRepository.getCachedBundle()
        _uiState.value = _uiState.value.copy(
            bundle = scenarioBundleState.value ?: _uiState.value.bundle,
            isRefreshingBundles = true,
            errorMessage = null,
            infoMessage = if (forceInfoMessage) "Refreshing simulator policy bundles." else _uiState.value.infoMessage
        )
        viewModelScope.launch {
            scenarioRepository.refreshBundle().onSuccess { scenarioBundleState.value = it }
            travelAdvisorRepository.refreshPolicyBundle().onSuccess { travelBundleState.value = it }
            plannerRepository.refreshBundle().onSuccess { pathwayBundleState.value = it }
            h1bDashboardRepository.refreshBundle().onSuccess { h1bBundleState.value = it }
            _uiState.value = _uiState.value.copy(
                isRefreshingBundles = false,
                infoMessage = if (forceInfoMessage) "Scenario policy bundles refreshed." else _uiState.value.infoMessage
            )
        }
    }

    private fun updateWorkingDraft(transform: ScenarioDraft.() -> ScenarioDraft) {
        val current = _uiState.value.workingDraft ?: return
        hasLocalChanges = true
        _uiState.value = _uiState.value.copy(
            workingDraft = current.transform().copy(updatedAt = timeProvider()),
            currentResult = null,
            errorMessage = null,
            infoMessage = null
        )
    }

    private inline fun <reified T : com.sidekick.opt_pal.data.model.ScenarioAssumptions> updateAssumptions(
        crossinline transform: T.() -> T
    ) {
        updateWorkingDraft {
            val typed = assumptions as? T ?: return@updateWorkingDraft this
            copy(assumptions = typed.transform())
        }
    }

    companion object {
        fun defaultAssumptionsFor(templateId: ScenarioTemplateId): com.sidekick.opt_pal.data.model.ScenarioAssumptions = when (templateId) {
            ScenarioTemplateId.JOB_LOSS_OR_INTERRUPTION -> JobLossScenarioAssumptions()
            ScenarioTemplateId.ADD_OR_SWITCH_EMPLOYER -> EmployerChangeScenarioAssumptions()
            ScenarioTemplateId.REPORTING_DEADLINE_MISSED -> ReportingDeadlineScenarioAssumptions()
            ScenarioTemplateId.INTERNATIONAL_TRAVEL -> TravelScenarioAssumptions()
            ScenarioTemplateId.H1B_CAP_CONTINUITY -> com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions()
            ScenarioTemplateId.PENDING_STEM_EXTENSION -> PendingStemExtensionScenarioAssumptions()
        }

        fun provideFactory(
            initialTemplateId: String?,
            initialDraftId: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScenarioSimulatorViewModel(
                    authRepository = AppModule.authRepository,
                    dashboardRepository = AppModule.dashboardRepository,
                    reportingRepository = AppModule.reportingRepository,
                    documentRepository = AppModule.documentRepository,
                    i983AssistantRepository = AppModule.i983AssistantRepository,
                    caseStatusRepository = AppModule.caseStatusRepository,
                    plannerRepository = AppModule.visaPathwayPlannerRepository,
                    h1bDashboardRepository = AppModule.h1bDashboardRepository,
                    travelAdvisorRepository = AppModule.travelAdvisorRepository,
                    policyAlertRepository = AppModule.policyAlertRepository,
                    scenarioRepository = AppModule.scenarioSimulatorRepository,
                    initialTemplateId = initialTemplateId,
                    initialDraftId = initialDraftId
                ) as T
            }
        }
    }
}

private data class ScenarioPrimaryObserved(
    val profile: com.sidekick.opt_pal.data.model.UserProfile?,
    val employments: List<com.sidekick.opt_pal.data.model.Employment>,
    val reporting: List<com.sidekick.opt_pal.data.model.ReportingObligation>,
    val documents: List<com.sidekick.opt_pal.data.model.DocumentMetadata>,
    val drafts: List<ScenarioDraft>
)

private data class ScenarioSecondaryObserved(
    val i983Drafts: List<com.sidekick.opt_pal.data.model.I983Draft>,
    val trackedCases: List<com.sidekick.opt_pal.data.model.UscisCaseTracker>,
    val plannerProfile: VisaPathwayProfile?,
    val h1bProfile: com.sidekick.opt_pal.data.model.H1bProfile?,
    val employerVerification: com.sidekick.opt_pal.data.model.H1bEmployerVerification?
)

private data class ScenarioTertiaryObserved(
    val timelineState: com.sidekick.opt_pal.data.model.H1bTimelineState?,
    val caseTracking: com.sidekick.opt_pal.data.model.H1bCaseTracking?,
    val evidence: com.sidekick.opt_pal.data.model.H1bEvidence?,
    val policyAlerts: List<com.sidekick.opt_pal.data.model.PolicyAlertCard>,
    val policyStates: List<com.sidekick.opt_pal.data.model.PolicyAlertState>
)

private data class ScenarioBundleObserved(
    val scenarioBundle: ScenarioSimulatorBundle?,
    val travelBundle: com.sidekick.opt_pal.data.model.TravelPolicyBundle?,
    val pathwayBundle: com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle?,
    val h1bBundle: com.sidekick.opt_pal.data.model.H1bDashboardBundle?
)

private data class ScenarioObserved(
    val primary: ScenarioPrimaryObserved,
    val secondary: ScenarioSecondaryObserved,
    val tertiary: ScenarioTertiaryObserved,
    val bundles: ScenarioBundleObserved,
    val entitlement: ScenarioSimulatorEntitlementState
)
