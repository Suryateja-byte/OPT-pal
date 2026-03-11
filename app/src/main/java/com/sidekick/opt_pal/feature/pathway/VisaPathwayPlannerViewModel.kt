package com.sidekick.opt_pal.feature.pathway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.pathway.VisaPathwayEngine
import com.sidekick.opt_pal.core.pathway.buildVisaPathwayEvidenceSnapshot
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertState
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayAssessment
import com.sidekick.opt_pal.data.model.VisaPathwayEmployerType
import com.sidekick.opt_pal.data.model.VisaPathwayEntitlementState
import com.sidekick.opt_pal.data.model.VisaPathwayH1bRegistrationStatus
import com.sidekick.opt_pal.data.model.VisaPathwayId
import com.sidekick.opt_pal.data.model.VisaPathwayO1EvidenceBucket
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerSummary
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.I983AssistantRepository
import com.sidekick.opt_pal.data.repository.PolicyAlertRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.data.repository.VisaPathwayPlannerRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class VisaPathwayPlannerUiState(
    val isLoading: Boolean = true,
    val isRefreshingBundle: Boolean = false,
    val isSavingProfile: Boolean = false,
    val entitlement: VisaPathwayEntitlementState = VisaPathwayEntitlementState(),
    val bundle: VisaPathwayPlannerBundle? = null,
    val userProfile: UserProfile? = null,
    val plannerProfile: VisaPathwayProfile = VisaPathwayProfile(),
    val employments: List<Employment> = emptyList(),
    val reportingObligations: List<ReportingObligation> = emptyList(),
    val documents: List<DocumentMetadata> = emptyList(),
    val i983Drafts: List<I983Draft> = emptyList(),
    val trackedCases: List<UscisCaseTracker> = emptyList(),
    val policyAlerts: List<PolicyAlertCard> = emptyList(),
    val policyStates: List<PolicyAlertState> = emptyList(),
    val assessments: List<VisaPathwayAssessment> = emptyList(),
    val summary: VisaPathwayPlannerSummary = VisaPathwayPlannerSummary(),
    val selectedPathwayId: VisaPathwayId? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val selectedAssessment: VisaPathwayAssessment?
        get() = assessments.firstOrNull { it.pathwayId == selectedPathwayId }
            ?: summary.topAssessment
            ?: temporaryAssessments.firstOrNull()

    val temporaryAssessments: List<VisaPathwayAssessment>
        get() = assessments.filter { !it.isEducationalOnly }

    val longTermAssessments: List<VisaPathwayAssessment>
        get() = assessments.filter { it.isEducationalOnly }
}

class VisaPathwayPlannerViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val reportingRepository: ReportingRepository,
    private val documentRepository: DocumentRepository,
    private val i983AssistantRepository: I983AssistantRepository,
    private val caseStatusRepository: CaseStatusRepository,
    private val policyAlertRepository: PolicyAlertRepository,
    private val plannerRepository: VisaPathwayPlannerRepository,
    private val engine: VisaPathwayEngine = VisaPathwayEngine(),
    private val initialPathwayId: String?,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VisaPathwayPlannerUiState(
            selectedPathwayId = initialPathwayId?.let(VisaPathwayId::fromWireValue)
        )
    )
    val uiState = _uiState.asStateFlow()
    private val entitlementState = MutableStateFlow(VisaPathwayEntitlementState())
    private val bundleState = MutableStateFlow<VisaPathwayPlannerBundle?>(null)

    private var currentUid: String? = null
    private var observationJob: Job? = null
    private var lastEntitlementFlag: Boolean? = null

    init {
        authRepository.getAuthState()
            .onEach { user ->
                currentUid = user?.uid
                lastEntitlementFlag = null
                observationJob?.cancel()
                if (user == null) {
                    _uiState.value = VisaPathwayPlannerUiState(
                        isLoading = false,
                        errorMessage = "User not logged in."
                    )
                } else {
                    loadBundle()
                    observeUserData(user.uid)
                }
            }
            .launchIn(viewModelScope)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun refreshBundle() {
        loadBundle(forceInfoMessage = true)
    }

    fun selectPathway(pathwayId: VisaPathwayId) {
        _uiState.value = _uiState.value.copy(selectedPathwayId = pathwayId)
    }

    fun markPreferredPathway(pathwayId: VisaPathwayId) {
        updatePlannerProfile { copy(preferredPathwayId = pathwayId.wireValue) }
        _uiState.value = _uiState.value.copy(selectedPathwayId = pathwayId)
    }

    fun updateDesiredContinuityDate(dateMillis: Long?) {
        updatePlannerProfile { copy(desiredContinuityDate = dateMillis) }
    }

    fun updateEmployerType(employerType: VisaPathwayEmployerType) {
        updatePlannerProfile { copy(employerType = employerType.wireValue) }
    }

    fun updateEmployerUsesEVerify(value: Boolean?) {
        updatePlannerProfile { copy(employerUsesEVerify = value) }
    }

    fun updateEmployerWillSponsorH1b(value: Boolean?) {
        updatePlannerProfile { copy(employerWillSponsorH1b = value) }
    }

    fun updateH1bRegistrationStatus(status: VisaPathwayH1bRegistrationStatus) {
        updatePlannerProfile { copy(h1bRegistrationStatus = status.wireValue) }
    }

    fun updateDegreeLevel(value: String) {
        updatePlannerProfile { copy(degreeLevel = value) }
    }

    fun updateHasPriorUsStemDegree(value: Boolean?) {
        updatePlannerProfile { copy(hasPriorUsStemDegree = value) }
    }

    fun updateRoleRelatedToDegree(value: Boolean?) {
        updatePlannerProfile { copy(roleDirectlyRelatedToDegree = value) }
    }

    fun updateHasPetitioningEmployerOrAgent(value: Boolean?) {
        updatePlannerProfile { copy(hasPetitioningEmployerOrAgent = value) }
    }

    fun toggleO1Evidence(bucket: VisaPathwayO1EvidenceBucket) {
        updatePlannerProfile {
            val next = o1EvidenceSignals.toMutableSet()
            if (!next.add(bucket.wireValue)) next.remove(bucket.wireValue)
            copy(o1EvidenceSignals = next.toList().sorted())
        }
    }

    fun updateStatusViolation(value: Boolean?) {
        updatePlannerProfile { copy(hasStatusViolation = value) }
    }

    fun updateArrestHistory(value: Boolean?) {
        updatePlannerProfile { copy(hasArrestHistory = value) }
    }

    fun updateUnauthorizedEmployment(value: Boolean?) {
        updatePlannerProfile { copy(hasUnauthorizedEmployment = value) }
    }

    fun updateRfeOrNoid(value: Boolean?) {
        updatePlannerProfile { copy(hasRfeOrNoid = value) }
    }

    private fun observeUserData(uid: String) {
        val primaryObservedFlow = combine(
            authRepository.getUserProfile(uid),
            plannerRepository.observeProfile(uid),
            dashboardRepository.getEmployments(uid),
            reportingRepository.getReportingObligations(uid),
            documentRepository.getDocuments(uid)
        ) { userProfile, plannerProfile, employments, reportingObligations, documents ->
            PrimaryObservedPlannerData(
                userProfile = userProfile,
                plannerProfile = plannerProfile,
                employments = employments,
                reportingObligations = reportingObligations,
                documents = documents
            )
        }
        val secondaryObservedFlow = combine(
            i983AssistantRepository.observeDrafts(uid),
            caseStatusRepository.observeTrackedCases(uid),
            policyAlertRepository.observePublishedAlerts(),
            policyAlertRepository.observeAlertStates(uid)
        ) { i983Drafts, trackedCases, policyAlerts, policyStates ->
            SecondaryObservedPlannerData(
                i983Drafts = i983Drafts,
                trackedCases = trackedCases,
                policyAlerts = policyAlerts,
                policyStates = policyStates
            )
        }
        val observedDataFlow = combine(
            primaryObservedFlow,
            secondaryObservedFlow
        ) { primary, secondary ->
            ObservedPlannerData(
                userProfile = primary.userProfile,
                plannerProfile = primary.plannerProfile,
                employments = primary.employments,
                reportingObligations = primary.reportingObligations,
                documents = primary.documents,
                i983Drafts = secondary.i983Drafts,
                trackedCases = secondary.trackedCases,
                policyAlerts = secondary.policyAlerts,
                policyStates = secondary.policyStates
            )
        }
        observationJob = combine(
            observedDataFlow,
            entitlementState,
            bundleState
        ) { observed, entitlement, bundle ->
            Triple(observed, entitlement, bundle)
        }.onEach { (observed, entitlement, bundle) ->
            maybeResolveEntitlement(observed.userProfile?.visaPathwayPlannerEnabled)
            val assessments = if (entitlement.isEnabled && bundle != null) {
                val snapshot = buildVisaPathwayEvidenceSnapshot(
                    profile = observed.userProfile,
                    plannerProfile = observed.plannerProfile,
                    employments = observed.employments,
                    reportingObligations = observed.reportingObligations,
                    documents = observed.documents,
                    i983Drafts = observed.i983Drafts,
                    trackedCases = observed.trackedCases,
                    policyAlerts = observed.policyAlerts,
                    policyStates = observed.policyStates,
                    bundle = bundle
                )
                engine.assess(
                    evidence = snapshot,
                    bundle = bundle,
                    now = timeProvider()
                )
            } else {
                emptyList()
            }
            val summary = engine.buildSummary(
                assessments = assessments,
                preferredPathwayId = observed.plannerProfile?.parsedPreferredPathwayId
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                entitlement = entitlement,
                bundle = bundle,
                userProfile = observed.userProfile,
                plannerProfile = observed.plannerProfile ?: VisaPathwayProfile(),
                employments = observed.employments,
                reportingObligations = observed.reportingObligations,
                documents = observed.documents,
                i983Drafts = observed.i983Drafts,
                trackedCases = observed.trackedCases,
                policyAlerts = observed.policyAlerts,
                policyStates = observed.policyStates,
                assessments = assessments,
                summary = summary,
                selectedPathwayId = resolveSelectedPathwayId(
                    current = _uiState.value.selectedPathwayId,
                    preferred = observed.plannerProfile?.parsedPreferredPathwayId,
                    summary = summary
                )
            )
        }.launchIn(viewModelScope)
    }

    private fun maybeResolveEntitlement(userFlag: Boolean?) {
        if (userFlag == lastEntitlementFlag) return
        lastEntitlementFlag = userFlag
        viewModelScope.launch {
            plannerRepository.resolveEntitlement(userFlag)
                .onSuccess { entitlement ->
                    entitlementState.value = entitlement
                }
                .onFailure { error ->
                    entitlementState.value = VisaPathwayEntitlementState(
                        isEnabled = false,
                        message = error.message ?: "Unable to resolve Visa Pathway Planner access."
                    )
                }
        }
    }

    private fun loadBundle(forceInfoMessage: Boolean = false) {
        val cached = plannerRepository.getCachedBundle()
        bundleState.value = cached
        _uiState.value = _uiState.value.copy(
            bundle = cached ?: _uiState.value.bundle,
            isRefreshingBundle = true,
            errorMessage = null,
            infoMessage = if (cached != null && forceInfoMessage) {
                "Using the cached planner bundle while refreshing."
            } else {
                _uiState.value.infoMessage
            }
        )
        viewModelScope.launch {
            plannerRepository.refreshBundle()
                .onSuccess { bundle ->
                    bundleState.value = bundle
                    _uiState.value = _uiState.value.copy(
                        bundle = bundle,
                        isRefreshingBundle = false,
                        infoMessage = if (forceInfoMessage) "Planner bundle refreshed." else _uiState.value.infoMessage
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingBundle = false,
                        errorMessage = if (_uiState.value.bundle == null) {
                            error.message ?: "Unable to load the planner bundle."
                        } else {
                            null
                        },
                        infoMessage = if (_uiState.value.bundle != null) {
                            "Using the cached planner bundle because refresh failed."
                        } else {
                            _uiState.value.infoMessage
                        }
                    )
                }
        }
    }

    private fun updatePlannerProfile(transform: VisaPathwayProfile.() -> VisaPathwayProfile) {
        val uid = currentUid ?: return
        val updated = _uiState.value.plannerProfile.transform()
        _uiState.value = _uiState.value.copy(
            plannerProfile = updated,
            isSavingProfile = true,
            errorMessage = null,
            infoMessage = null
        )
        viewModelScope.launch {
            plannerRepository.saveProfile(uid, updated)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSavingProfile = false,
                        infoMessage = "Planner inputs saved."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSavingProfile = false,
                        errorMessage = error.message ?: "Unable to save planner inputs."
                    )
                }
        }
    }

    private fun resolveSelectedPathwayId(
        current: VisaPathwayId?,
        preferred: VisaPathwayId?,
        summary: VisaPathwayPlannerSummary
    ): VisaPathwayId? {
        return when {
            current != null -> current
            preferred != null -> preferred
            summary.topAssessment != null -> summary.topAssessment.pathwayId
            else -> null
        }
    }

    companion object {
        fun provideFactory(initialPathwayId: String?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VisaPathwayPlannerViewModel(
                    authRepository = AppModule.authRepository,
                    dashboardRepository = AppModule.dashboardRepository,
                    reportingRepository = AppModule.reportingRepository,
                    documentRepository = AppModule.documentRepository,
                    i983AssistantRepository = AppModule.i983AssistantRepository,
                    caseStatusRepository = AppModule.caseStatusRepository,
                    policyAlertRepository = AppModule.policyAlertRepository,
                    plannerRepository = AppModule.visaPathwayPlannerRepository,
                    initialPathwayId = initialPathwayId
                ) as T
            }
        }
    }
}

private data class ObservedPlannerData(
    val userProfile: UserProfile?,
    val plannerProfile: VisaPathwayProfile?,
    val employments: List<Employment>,
    val reportingObligations: List<ReportingObligation>,
    val documents: List<DocumentMetadata>,
    val i983Drafts: List<I983Draft>,
    val trackedCases: List<UscisCaseTracker>,
    val policyAlerts: List<PolicyAlertCard>,
    val policyStates: List<PolicyAlertState>
)

private data class PrimaryObservedPlannerData(
    val userProfile: UserProfile?,
    val plannerProfile: VisaPathwayProfile?,
    val employments: List<Employment>,
    val reportingObligations: List<ReportingObligation>,
    val documents: List<DocumentMetadata>
)

private data class SecondaryObservedPlannerData(
    val i983Drafts: List<I983Draft>,
    val trackedCases: List<UscisCaseTracker>,
    val policyAlerts: List<PolicyAlertCard>,
    val policyStates: List<PolicyAlertState>
)
