package com.sidekick.opt_pal.feature.h1b

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.h1b.H1bDashboardComputedState
import com.sidekick.opt_pal.core.h1b.H1bDashboardEngine
import com.sidekick.opt_pal.data.model.CapGapAssessment
import com.sidekick.opt_pal.data.model.CapSeasonTimeline
import com.sidekick.opt_pal.data.model.EmployerVerificationSummary
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.H1bCaseTracking
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bEmployerVerification
import com.sidekick.opt_pal.data.model.H1bEVerifyStatus
import com.sidekick.opt_pal.data.model.H1bEvidence
import com.sidekick.opt_pal.data.model.H1bProfile
import com.sidekick.opt_pal.data.model.H1bReadinessSummary
import com.sidekick.opt_pal.data.model.H1bTimelineState
import com.sidekick.opt_pal.data.model.H1bWorkflowStage
import com.sidekick.opt_pal.data.model.H1bCaseTrackingState
import com.sidekick.opt_pal.data.model.UscisTrackedFormType
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayEmployerType
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.H1bDashboardRepository
import com.sidekick.opt_pal.data.repository.VisaPathwayPlannerRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class H1bDashboardUiState(
    val isLoading: Boolean = true,
    val isRefreshingBundle: Boolean = false,
    val isSearchingEmployerHistory: Boolean = false,
    val isSavingSnapshot: Boolean = false,
    val isSavingInputs: Boolean = false,
    val isTrackingCase: Boolean = false,
    val refreshingCaseId: String? = null,
    val bundle: H1bDashboardBundle? = null,
    val userProfile: UserProfile? = null,
    val h1bProfile: H1bProfile = H1bProfile(),
    val employerVerification: H1bEmployerVerification = H1bEmployerVerification(),
    val timelineState: H1bTimelineState = H1bTimelineState(),
    val caseTracking: H1bCaseTracking = H1bCaseTracking(),
    val evidence: H1bEvidence = H1bEvidence(),
    val readinessSummary: H1bReadinessSummary = H1bReadinessSummary(),
    val employerVerificationSummary: EmployerVerificationSummary = EmployerVerificationSummary(),
    val capSeasonTimeline: CapSeasonTimeline = CapSeasonTimeline(),
    val capGapAssessment: CapGapAssessment = CapGapAssessment(),
    val caseTrackingState: H1bCaseTrackingState = H1bCaseTrackingState(),
    val receiptNumberInput: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null
)

class H1bDashboardViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val caseStatusRepository: CaseStatusRepository,
    private val plannerRepository: VisaPathwayPlannerRepository,
    private val h1bDashboardRepository: H1bDashboardRepository,
    private val engine: H1bDashboardEngine = H1bDashboardEngine(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(H1bDashboardUiState())
    val uiState = _uiState.asStateFlow()

    private val bundleState = MutableStateFlow<H1bDashboardBundle?>(null)
    private var currentUid: String? = null
    private var observationJob: Job? = null

    init {
        authRepository.getAuthState()
            .onEach { user ->
                currentUid = user?.uid
                observationJob?.cancel()
                if (user == null) {
                    _uiState.value = H1bDashboardUiState(
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
        _uiState.value = _uiState.value.copy(
            infoMessage = null,
            errorMessage = null
        )
    }

    fun refreshBundle() {
        loadBundle(forceInfoMessage = true)
    }

    fun onReceiptNumberChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            receiptNumberInput = value.uppercase().filter { it.isLetterOrDigit() }.take(13)
        )
    }

    fun updateEmployerName(value: String) {
        persistProfile { copy(employerName = value) }
    }

    fun updateEmployerCity(value: String) {
        persistProfile { copy(employerCity = value) }
    }

    fun updateEmployerState(value: String) {
        persistProfile { copy(employerState = value.uppercase()) }
    }

    fun updateFeinLastFour(value: String) {
        persistProfile { copy(feinLastFour = value.filter(Char::isDigit).takeLast(4)) }
    }

    fun updateEmployerType(value: VisaPathwayEmployerType) {
        persistProfile { copy(employerType = value.wireValue) }
    }

    fun updateSponsorIntent(value: Boolean?) {
        persistProfile { copy(selfReportedSponsorIntent = value) }
    }

    fun updateRoleMatchesSpecialtyOccupation(value: Boolean?) {
        persistProfile { copy(roleMatchesSpecialtyOccupation = value) }
    }

    fun updateWorkflowStage(stage: H1bWorkflowStage) {
        persistTimeline {
            copy(
                workflowStage = stage.wireValue,
                selectedRegistration = if (stage.ordinal >= H1bWorkflowStage.SELECTED.ordinal) true else selectedRegistration,
                filedPetition = if (stage.ordinal >= H1bWorkflowStage.PETITION_FILED.ordinal) true else filedPetition
            )
        }
    }

    fun updateRequestedChangeOfStatus(value: Boolean?) {
        persistTimeline { copy(requestedChangeOfStatus = value) }
    }

    fun updateRequestedConsularProcessing(value: Boolean?) {
        persistTimeline { copy(requestedConsularProcessing = value) }
    }

    fun updateSelectedRegistration(value: Boolean?) {
        persistTimeline { copy(selectedRegistration = value) }
    }

    fun updateFiledPetition(value: Boolean?) {
        persistTimeline { copy(filedPetition = value) }
    }

    fun updateHasEmployerLetter(value: Boolean?) {
        persistEvidence { copy(hasEmployerLetter = value) }
    }

    fun updateHasWageInfo(value: Boolean?) {
        persistEvidence { copy(hasWageInfo = value) }
    }

    fun updateHasDegreeMatchEvidence(value: Boolean?) {
        persistEvidence { copy(hasDegreeMatchEvidence = value) }
    }

    fun updateHasRegistrationConfirmation(value: Boolean?) {
        persistEvidence { copy(hasRegistrationConfirmation = value) }
    }

    fun updateHasReceiptNotice(value: Boolean?) {
        persistEvidence { copy(hasReceiptNotice = value) }
    }

    fun updateHasCapExemptSupport(value: Boolean?) {
        persistEvidence { copy(hasCapExemptSupport = value) }
    }

    fun updateCapGapTravelPlanned(value: Boolean?) {
        persistEvidence { copy(capGapTravelPlanned = value) }
    }

    fun updateHasRfeOrNoid(value: Boolean?) {
        persistEvidence { copy(hasRfeOrNoid = value) }
    }

    fun refreshEmployerHistory() {
        val profile = _uiState.value.h1bProfile
        if (profile.employerName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Add the employer name before searching employer history.")
            return
        }
        val uid = currentUid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearchingEmployerHistory = true,
                errorMessage = null,
                infoMessage = null
            )
            h1bDashboardRepository.searchEmployerHistory(
                employerName = profile.employerName,
                employerCity = profile.employerCity.ifBlank { null },
                employerState = profile.employerState.ifBlank { null }
            ).onSuccess { history ->
                val nextVerification = _uiState.value.employerVerification.copy(employerHistory = history)
                h1bDashboardRepository.saveEmployerVerification(uid, nextVerification)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(
                            isSearchingEmployerHistory = false,
                            infoMessage = "USCIS employer-history snapshot updated."
                        )
                    }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            isSearchingEmployerHistory = false,
                            errorMessage = error.message ?: "Unable to save the employer-history snapshot."
                        )
                    }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSearchingEmployerHistory = false,
                    errorMessage = error.message ?: "Unable to search employer history right now."
                )
            }
        }
    }

    fun saveEVerifySnapshot(status: H1bEVerifyStatus) {
        val profile = _uiState.value.h1bProfile
        if (profile.employerName.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Add the employer name before saving an E-Verify snapshot.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingSnapshot = true,
                errorMessage = null,
                infoMessage = null
            )
            h1bDashboardRepository.saveEVerifySnapshot(
                employerName = profile.employerName,
                employerCity = profile.employerCity.ifBlank { null },
                employerState = profile.employerState.ifBlank { null },
                status = status
            ).onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSavingSnapshot = false,
                    infoMessage = "E-Verify snapshot saved."
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSavingSnapshot = false,
                    errorMessage = error.message ?: "Unable to save the E-Verify snapshot."
                )
            }
        }
    }

    fun trackI129Case() {
        val uid = currentUid ?: return
        val receiptNumber = _uiState.value.receiptNumberInput.trim().uppercase()
        if (!RECEIPT_NUMBER_REGEX.matches(receiptNumber)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Receipt number must match ABC1234567890.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTrackingCase = true,
                errorMessage = null,
                infoMessage = null
            )
            caseStatusRepository.trackCase(receiptNumber, UscisTrackedFormType.I129)
                .onSuccess { caseId ->
                    val caseTrackingResult = h1bDashboardRepository.saveCaseTracking(
                        uid = uid,
                        caseTracking = _uiState.value.caseTracking.copy(
                            linkedCaseId = caseId,
                            linkedReceiptNumber = receiptNumber
                        )
                    )
                    val timelineResult = h1bDashboardRepository.saveTimelineState(
                        uid = uid,
                        timelineState = _uiState.value.timelineState.copy(
                            receiptNumber = receiptNumber
                        )
                    )
                    val persistenceError = caseTrackingResult.exceptionOrNull() ?: timelineResult.exceptionOrNull()
                    if (persistenceError != null) {
                        _uiState.value = _uiState.value.copy(
                            isTrackingCase = false,
                            errorMessage = persistenceError.message ?: "Unable to save the linked I-129 receipt."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isTrackingCase = false,
                            receiptNumberInput = "",
                            infoMessage = "I-129 receipt linked to the H-1B dashboard."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isTrackingCase = false,
                        errorMessage = error.message ?: "Unable to track this I-129 receipt right now."
                    )
                }
        }
    }

    fun refreshLinkedCase() {
        val caseId = _uiState.value.caseTrackingState.linkedCaseId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                refreshingCaseId = caseId,
                errorMessage = null,
                infoMessage = null
            )
            caseStatusRepository.refreshCase(caseId)
                .onSuccess { refresh ->
                    _uiState.value = _uiState.value.copy(
                        refreshingCaseId = null,
                        infoMessage = when {
                            !refresh.refreshed && refresh.cooldownRemainingMinutes > 0 ->
                                "USCIS refresh is on cooldown. Try again in about ${refresh.cooldownRemainingMinutes} minutes."

                            refresh.statusChanged ->
                                "USCIS reported a new I-129 update."

                            else ->
                                "USCIS status checked."
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        refreshingCaseId = null,
                        errorMessage = error.message ?: "Unable to refresh the linked USCIS case."
                    )
                }
        }
    }

    private fun observeUserData(uid: String) {
        val primaryFlow = combine(
            authRepository.getUserProfile(uid),
            dashboardRepository.getEmployments(uid),
            plannerRepository.observeProfile(uid),
            h1bDashboardRepository.observeProfile(uid),
            h1bDashboardRepository.observeEmployerVerification(uid)
        ) { userProfile, employments, plannerProfile, h1bProfile, employerVerification ->
            PrimaryObservedH1bData(
                userProfile = userProfile,
                employments = employments,
                plannerProfile = plannerProfile,
                h1bProfile = h1bProfile,
                employerVerification = employerVerification
            )
        }
        val secondaryFlow = combine(
            h1bDashboardRepository.observeTimelineState(uid),
            h1bDashboardRepository.observeCaseTracking(uid),
            h1bDashboardRepository.observeEvidence(uid),
            caseStatusRepository.observeTrackedCases(uid),
            bundleState
        ) { timelineState, caseTracking, evidence, trackedCases, bundle ->
            SecondaryObservedH1bData(
                timelineState = timelineState,
                caseTracking = caseTracking,
                evidence = evidence,
                trackedCases = trackedCases,
                bundle = bundle
            )
        }
        val observedFlow = combine(primaryFlow, secondaryFlow) { primary, secondary ->
            ObservedH1bData(
                userProfile = primary.userProfile,
                employments = primary.employments,
                plannerProfile = primary.plannerProfile,
                h1bProfile = primary.h1bProfile,
                employerVerification = primary.employerVerification,
                timelineState = secondary.timelineState,
                caseTracking = secondary.caseTracking,
                evidence = secondary.evidence,
                trackedCases = secondary.trackedCases,
                bundle = secondary.bundle
            )
        }
        observationJob = observedFlow
            .onEach { observed ->
                val effectiveProfile = prefillProfile(
                    storedProfile = observed.h1bProfile,
                    plannerProfile = observed.plannerProfile,
                    employments = observed.employments
                )
                val computed = observed.bundle?.let { bundle ->
                    engine.build(
                        userProfile = observed.userProfile,
                        h1bProfile = effectiveProfile,
                        employerVerification = observed.employerVerification ?: H1bEmployerVerification(),
                        timelineState = observed.timelineState ?: H1bTimelineState(),
                        caseTracking = observed.caseTracking ?: H1bCaseTracking(),
                        evidence = observed.evidence ?: H1bEvidence(),
                        trackedCases = observed.trackedCases,
                        bundle = bundle,
                        now = timeProvider()
                    )
                } ?: H1bDashboardComputedState(
                    readinessSummary = H1bReadinessSummary(),
                    employerVerificationSummary = EmployerVerificationSummary(),
                    capSeasonTimeline = CapSeasonTimeline(),
                    capGapAssessment = CapGapAssessment(),
                    caseTrackingState = H1bCaseTrackingState()
                )
                val currentState = _uiState.value
                _uiState.value = currentState.copy(
                    isLoading = false,
                    bundle = observed.bundle,
                    userProfile = observed.userProfile,
                    h1bProfile = effectiveProfile,
                    employerVerification = observed.employerVerification ?: H1bEmployerVerification(),
                    timelineState = observed.timelineState ?: H1bTimelineState(),
                    caseTracking = observed.caseTracking ?: H1bCaseTracking(),
                    evidence = observed.evidence ?: H1bEvidence(),
                    readinessSummary = computed.readinessSummary,
                    employerVerificationSummary = computed.employerVerificationSummary,
                    capSeasonTimeline = computed.capSeasonTimeline,
                    capGapAssessment = computed.capGapAssessment,
                    caseTrackingState = computed.caseTrackingState
                )
            }
            .launchIn(viewModelScope)
    }

    private fun loadBundle(forceInfoMessage: Boolean = false) {
        val cached = h1bDashboardRepository.getCachedBundle()
        bundleState.value = cached
        _uiState.value = _uiState.value.copy(
            bundle = cached ?: _uiState.value.bundle,
            isRefreshingBundle = true,
            errorMessage = null,
            infoMessage = if (cached != null && forceInfoMessage) {
                "Using the cached H-1B bundle while refreshing."
            } else {
                _uiState.value.infoMessage
            }
        )
        viewModelScope.launch {
            h1bDashboardRepository.refreshBundle()
                .onSuccess { bundle ->
                    bundleState.value = bundle
                    _uiState.value = _uiState.value.copy(
                        bundle = bundle,
                        isRefreshingBundle = false,
                        infoMessage = if (forceInfoMessage) "H-1B dashboard bundle refreshed." else _uiState.value.infoMessage
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingBundle = false,
                        errorMessage = if (_uiState.value.bundle == null) {
                            error.message ?: "Unable to load the H-1B dashboard bundle."
                        } else {
                            null
                        },
                        infoMessage = if (_uiState.value.bundle != null) {
                            "Using the cached H-1B dashboard bundle because refresh failed."
                        } else {
                            _uiState.value.infoMessage
                        }
                    )
                }
        }
    }

    private fun persistProfile(transform: H1bProfile.() -> H1bProfile) {
        val uid = currentUid ?: return
        val updated = _uiState.value.h1bProfile.transform()
        saveState(
            onStart = { copy(h1bProfile = updated, isSavingInputs = true) },
            save = { h1bDashboardRepository.saveProfile(uid, updated) },
            successMessage = "H-1B profile saved."
        )
    }

    private fun persistTimeline(transform: H1bTimelineState.() -> H1bTimelineState) {
        val uid = currentUid ?: return
        val updated = _uiState.value.timelineState.transform()
        saveState(
            onStart = { copy(timelineState = updated, isSavingInputs = true) },
            save = { h1bDashboardRepository.saveTimelineState(uid, updated) },
            successMessage = "H-1B timeline saved."
        )
    }

    private fun persistEvidence(transform: H1bEvidence.() -> H1bEvidence) {
        val uid = currentUid ?: return
        val updated = _uiState.value.evidence.transform()
        saveState(
            onStart = { copy(evidence = updated, isSavingInputs = true) },
            save = { h1bDashboardRepository.saveEvidence(uid, updated) },
            successMessage = "H-1B evidence checklist saved."
        )
    }

    private fun saveState(
        onStart: H1bDashboardUiState.() -> H1bDashboardUiState,
        save: suspend () -> Result<Unit>,
        successMessage: String
    ) {
        _uiState.value = _uiState.value.onStart().copy(errorMessage = null)
        viewModelScope.launch {
            save().onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSavingInputs = false,
                    infoMessage = successMessage
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSavingInputs = false,
                    errorMessage = error.message ?: "Unable to save the H-1B dashboard inputs."
                )
            }
        }
    }

    private fun prefillProfile(
        storedProfile: H1bProfile?,
        plannerProfile: VisaPathwayProfile?,
        employments: List<Employment>
    ): H1bProfile {
        val currentEmployment = employments
            .sortedByDescending { it.startDate }
            .firstOrNull { it.endDate == null }
            ?: employments.maxByOrNull { it.startDate }
        val base = storedProfile ?: H1bProfile()
        return base.copy(
            employerName = base.employerName.ifBlank { currentEmployment?.employerName.orEmpty() },
            employerType = if (base.parsedEmployerType == VisaPathwayEmployerType.UNKNOWN) {
                plannerProfile?.employerType ?: base.employerType
            } else {
                base.employerType
            },
            selfReportedSponsorIntent = base.selfReportedSponsorIntent ?: plannerProfile?.employerWillSponsorH1b,
            roleMatchesSpecialtyOccupation = base.roleMatchesSpecialtyOccupation ?: plannerProfile?.roleDirectlyRelatedToDegree
        )
    }

    companion object {
        private val RECEIPT_NUMBER_REGEX = Regex("^[A-Z]{3}[0-9]{10}$")

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return H1bDashboardViewModel(
                    authRepository = AppModule.authRepository,
                    dashboardRepository = AppModule.dashboardRepository,
                    caseStatusRepository = AppModule.caseStatusRepository,
                    plannerRepository = AppModule.visaPathwayPlannerRepository,
                    h1bDashboardRepository = AppModule.h1bDashboardRepository
                ) as T
            }
        }
    }
}

private data class ObservedH1bData(
    val userProfile: UserProfile?,
    val employments: List<Employment>,
    val plannerProfile: VisaPathwayProfile?,
    val h1bProfile: H1bProfile?,
    val employerVerification: H1bEmployerVerification?,
    val timelineState: H1bTimelineState?,
    val caseTracking: H1bCaseTracking?,
    val evidence: H1bEvidence?,
    val trackedCases: List<com.sidekick.opt_pal.data.model.UscisCaseTracker>,
    val bundle: H1bDashboardBundle?
)

private data class PrimaryObservedH1bData(
    val userProfile: UserProfile?,
    val employments: List<Employment>,
    val plannerProfile: VisaPathwayProfile?,
    val h1bProfile: H1bProfile?,
    val employerVerification: H1bEmployerVerification?
)

private data class SecondaryObservedH1bData(
    val timelineState: H1bTimelineState?,
    val caseTracking: H1bCaseTracking?,
    val evidence: H1bEvidence?,
    val trackedCases: List<com.sidekick.opt_pal.data.model.UscisCaseTracker>,
    val bundle: H1bDashboardBundle?
)
