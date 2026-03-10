package com.sidekick.opt_pal.feature.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.travel.TravelRulesEngine
import com.sidekick.opt_pal.core.travel.buildTravelEvidenceSnapshot
import com.sidekick.opt_pal.data.model.TravelAssessment
import com.sidekick.opt_pal.data.model.TravelEntitlementState
import com.sidekick.opt_pal.data.model.TravelEvidenceSnapshot
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import com.sidekick.opt_pal.data.model.TravelScenario
import com.sidekick.opt_pal.data.model.TravelTripInput
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.TravelAdvisorRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class TravelDateField {
    DEPARTURE,
    RETURN,
    PASSPORT_EXPIRATION,
    VISA_EXPIRATION,
    I20_SIGNATURE,
    EAD_EXPIRATION
}

data class TravelAdvisorUiState(
    val isLoading: Boolean = true,
    val isRefreshingPolicy: Boolean = false,
    val profile: UserProfile? = null,
    val entitlement: TravelEntitlementState = TravelEntitlementState(),
    val policyBundle: TravelPolicyBundle? = null,
    val evidenceSnapshot: TravelEvidenceSnapshot = TravelEvidenceSnapshot(),
    val tripInput: TravelTripInput = TravelTripInput(),
    val assessment: TravelAssessment? = null,
    val sourceLabels: List<String> = emptyList(),
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val canRunAssessment: Boolean
        get() = entitlement.isEnabled && policyBundle != null
}

class TravelAdvisorViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val documentRepository: DocumentRepository,
    private val travelAdvisorRepository: TravelAdvisorRepository,
    private val rulesEngine: TravelRulesEngine = TravelRulesEngine(),
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(TravelAdvisorUiState())
    val uiState = _uiState.asStateFlow()

    private var currentUid: String? = null
    private var observationJob: Job? = null
    private var lastEntitlementFlag: Boolean? = null
    private var hasResolvedEntitlement = false

    init {
        authRepository.getAuthState()
            .onEach { user ->
                currentUid = user?.uid
                lastEntitlementFlag = null
                hasResolvedEntitlement = false
                if (user == null) {
                    observationJob?.cancel()
                    _uiState.value = TravelAdvisorUiState(
                        isLoading = false,
                        errorMessage = "User not logged in."
                    )
                } else {
                    loadPolicyBundle()
                    observeUserData(user.uid)
                }
            }
            .launchIn(viewModelScope)
    }

    fun refreshPolicyBundle() {
        loadPolicyBundle(forceInfoMessage = true)
    }

    fun onDestinationCountryChanged(value: String) = updateTripInput {
        copy(destinationCountry = value)
    }

    fun onPassportIssuingCountryChanged(value: String) = updateTripInput {
        copy(passportIssuingCountry = value)
    }

    fun onVisaClassChanged(value: String) = updateTripInput {
        copy(visaClass = value)
    }

    fun onScenarioSelected(value: TravelScenario) = updateTripInput {
        copy(travelScenario = value)
    }

    fun onOnlyContiguousTravelChanged(value: Boolean) = updateTripInput {
        copy(onlyCanadaMexicoAdjacentIslands = value)
    }

    fun onNeedsNewVisaChanged(value: Boolean) = updateTripInput {
        copy(needsNewVisa = value)
    }

    fun onVisaRenewalOutsideResidenceChanged(value: Boolean) = updateTripInput {
        copy(visaRenewalOutsideResidence = value)
    }

    fun onEmploymentProofChanged(value: Boolean) = updateTripInput {
        copy(hasEmploymentOrOfferProof = value)
    }

    fun onCapGapChanged(value: Boolean) = updateTripInput {
        copy(capGapActive = value)
    }

    fun onSensitiveIssueChanged(value: Boolean) = updateTripInput {
        copy(hasRfeStatusIssueOrArrestHistory = value)
    }

    fun onHasOriginalEadChanged(value: Boolean) = updateTripInput {
        copy(hasOriginalEadInHand = value)
    }

    fun onDateSelected(field: TravelDateField, millis: Long?) {
        updateTripInput {
            when (field) {
                TravelDateField.DEPARTURE -> copy(departureDate = millis)
                TravelDateField.RETURN -> copy(plannedReturnDate = millis)
                TravelDateField.PASSPORT_EXPIRATION -> copy(passportExpirationDate = millis)
                TravelDateField.VISA_EXPIRATION -> copy(visaExpirationDate = millis)
                TravelDateField.I20_SIGNATURE -> copy(i20TravelSignatureDate = millis)
                TravelDateField.EAD_EXPIRATION -> copy(eadExpirationDate = millis)
            }
        }
    }

    fun runAssessment() {
        val state = _uiState.value
        if (!state.entitlement.isEnabled) {
            AnalyticsLogger.logTravelEntitlementBlocked(state.entitlement.source.name)
            _uiState.value = state.copy(
                errorMessage = state.entitlement.message
            )
            return
        }
        val policyBundle = state.policyBundle
        if (policyBundle == null) {
            _uiState.value = state.copy(errorMessage = "Travel policy bundle is still loading.")
            return
        }

        val validationError = validateTripInput(state.tripInput)
        if (validationError != null) {
            _uiState.value = state.copy(errorMessage = validationError)
            return
        }

        AnalyticsLogger.logTravelAssessmentRun()
        val assessment = rulesEngine.assess(
            tripInput = state.tripInput,
            evidence = state.evidenceSnapshot,
            policyBundle = policyBundle,
            now = timeProvider()
        )
        AnalyticsLogger.logTravelAssessmentOutcome(assessment.outcome.name)
        assessment.checklistItems
            .filterNot { it.status == com.sidekick.opt_pal.data.model.TravelChecklistStatus.PASS }
            .forEach { item ->
                AnalyticsLogger.logTravelRuleBlocked(item.ruleId.name)
            }
        _uiState.value = state.copy(
            assessment = assessment,
            errorMessage = null,
            infoMessage = "Assessment updated with policy bundle ${assessment.policyVersion}."
        )
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    private fun observeUserData(uid: String) {
        observationJob?.cancel()
        observationJob = combine(
            authRepository.getUserProfile(uid),
            documentRepository.getDocuments(uid),
            dashboardRepository.getEmployments(uid)
        ) { profile, documents, employments ->
            Triple(profile, documents, employments)
        }.onEach { (profile, documents, employments) ->
            maybeResolveEntitlement(profile?.travelAdvisorEnabled)
            val evidence = buildTravelEvidenceSnapshot(
                documents = documents,
                profile = profile,
                employments = employments,
                now = timeProvider()
            )
            val mergedInput = mergeTripInputWithEvidence(
                current = _uiState.value.tripInput,
                evidence = evidence,
                profile = profile,
                now = timeProvider()
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                profile = profile,
                evidenceSnapshot = evidence,
                tripInput = mergedInput,
                sourceLabels = buildSourceLabels(evidence)
            )
        }.launchIn(viewModelScope)
    }

    private fun loadPolicyBundle(forceInfoMessage: Boolean = false) {
        val cached = travelAdvisorRepository.getCachedPolicyBundle()
        _uiState.value = _uiState.value.copy(
            policyBundle = cached ?: _uiState.value.policyBundle,
            isRefreshingPolicy = true,
            errorMessage = null,
            infoMessage = if (cached != null && forceInfoMessage) {
                "Using cached travel policy bundle while refreshing."
            } else {
                _uiState.value.infoMessage
            }
        )
        viewModelScope.launch {
            travelAdvisorRepository.refreshPolicyBundle()
                .onSuccess { bundle ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingPolicy = false,
                        policyBundle = bundle,
                        infoMessage = if (forceInfoMessage) {
                            "Travel policy bundle refreshed."
                        } else {
                            _uiState.value.infoMessage
                        }
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingPolicy = false,
                        errorMessage = if (_uiState.value.policyBundle == null) {
                            throwable.message ?: "Unable to load the travel policy bundle."
                        } else {
                            null
                        },
                        infoMessage = if (_uiState.value.policyBundle != null) {
                            "Using cached travel policy bundle because refresh failed."
                        } else {
                            _uiState.value.infoMessage
                        }
                    )
                }
        }
    }

    private fun maybeResolveEntitlement(travelAdvisorEnabled: Boolean?) {
        if (hasResolvedEntitlement && travelAdvisorEnabled == lastEntitlementFlag) {
            return
        }
        hasResolvedEntitlement = true
        lastEntitlementFlag = travelAdvisorEnabled
        viewModelScope.launch {
            travelAdvisorRepository.resolveEntitlement(travelAdvisorEnabled)
                .onSuccess { entitlement ->
                    if (!entitlement.isEnabled) {
                        AnalyticsLogger.logTravelEntitlementBlocked(entitlement.source.name)
                    }
                    _uiState.value = _uiState.value.copy(entitlement = entitlement)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        entitlement = TravelEntitlementState(
                            isEnabled = false,
                            message = throwable.message ?: "Unable to determine travel-feature access."
                        )
                    )
                }
        }
    }

    private fun updateTripInput(transform: TravelTripInput.() -> TravelTripInput) {
        _uiState.value = _uiState.value.copy(
            tripInput = _uiState.value.tripInput.transform(),
            assessment = null,
            errorMessage = null,
            infoMessage = null
        )
    }

    companion object {
        fun buildSourceLabels(evidence: TravelEvidenceSnapshot): List<String> {
            return listOfNotNull(
                evidence.passportSourceLabel,
                evidence.visaSourceLabel,
                evidence.i20SourceLabel,
                evidence.eadSourceLabel
            ).distinct()
        }

        fun mergeTripInputWithEvidence(
            current: TravelTripInput,
            evidence: TravelEvidenceSnapshot,
            profile: UserProfile?,
            now: Long
        ): TravelTripInput {
            return current.copy(
                travelScenario = current.travelScenario ?: inferTravelScenario(profile, evidence, now),
                passportIssuingCountry = current.passportIssuingCountry.ifBlank {
                    evidence.passportIssuingCountry.orEmpty()
                },
                passportExpirationDate = current.passportExpirationDate ?: evidence.passportExpirationDate,
                visaClass = current.visaClass.ifBlank {
                    evidence.visaClass ?: "F-1"
                },
                visaExpirationDate = current.visaExpirationDate ?: evidence.visaExpirationDate,
                i20TravelSignatureDate = current.i20TravelSignatureDate ?: evidence.i20TravelSignatureDate,
                eadExpirationDate = current.eadExpirationDate ?: evidence.eadExpirationDate ?: evidence.optEndDate,
                hasEmploymentOrOfferProof = current.hasEmploymentOrOfferProof ?: evidence.hasCurrentEmploymentRecord,
                hasOriginalEadInHand = current.hasOriginalEadInHand ?: (evidence.eadExpirationDate != null)
            )
        }

        fun inferTravelScenario(
            profile: UserProfile?,
            evidence: TravelEvidenceSnapshot,
            now: Long
        ): TravelScenario {
            val optEndDate = profile?.optEndDate ?: evidence.optEndDate
            if (optEndDate != null) {
                val gracePeriodEnd = optEndDate + 60L * 24L * 60L * 60L * 1000L
                if (now in (optEndDate + 1)..gracePeriodEnd) {
                    return TravelScenario.GRACE_PERIOD
                }
            }
            return when (profile?.optType?.lowercase()) {
                "stem" -> TravelScenario.APPROVED_STEM_OPT
                else -> TravelScenario.APPROVED_POST_COMPLETION_OPT
            }
        }

        fun validateTripInput(input: TravelTripInput): String? {
            if (input.departureDate == null || input.plannedReturnDate == null) {
                return "Add both departure and return dates."
            }
            if (input.plannedReturnDate < input.departureDate) {
                return "Return date must be after the departure date."
            }
            if (input.destinationCountry.isBlank()) {
                return "Enter the destination country."
            }
            if (input.onlyCanadaMexicoAdjacentIslands == null) {
                return "Confirm whether the trip is limited to Canada, Mexico, or adjacent islands."
            }
            if (input.needsNewVisa == null) {
                return "Confirm whether you will need a new F-1 visa."
            }
            if (input.visaRenewalOutsideResidence == null) {
                return "Confirm whether any visa renewal would be outside your country of nationality or residence."
            }
            if (input.travelScenario == null) {
                return "Choose the OPT travel scenario that matches your case."
            }
            if (input.capGapActive == null) {
                return "Confirm whether cap-gap applies to this trip."
            }
            if (input.hasRfeStatusIssueOrArrestHistory == null) {
                return "Confirm whether there is any RFE, status issue, or arrest history involved."
            }
            if (input.passportIssuingCountry.isBlank() || input.passportExpirationDate == null) {
                return "Add your passport issuing country and expiration date."
            }
            if (input.visaClass.isBlank()) {
                return "Enter your current visa class."
            }
            if (input.visaExpirationDate == null && input.needsNewVisa != true) {
                return "Add your current visa expiration date."
            }
            if (input.i20TravelSignatureDate == null) {
                return "Add the date of your most recent I-20 travel signature."
            }
            if (input.travelScenario in setOf(
                    TravelScenario.APPROVED_POST_COMPLETION_OPT,
                    TravelScenario.APPROVED_STEM_OPT,
                    TravelScenario.PENDING_STEM_EXTENSION
                )
            ) {
                if (input.hasEmploymentOrOfferProof == null) {
                    return "Confirm whether you have current employment or offer proof."
                }
                if (input.hasOriginalEadInHand == null) {
                    return "Confirm whether you have the original EAD in hand."
                }
                if (input.eadExpirationDate == null) {
                    return "Add the EAD expiration date for this travel scenario."
                }
            }
            return null
        }

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TravelAdvisorViewModel(
                    authRepository = AppModule.authRepository,
                    dashboardRepository = AppModule.dashboardRepository,
                    documentRepository = AppModule.documentRepository,
                    travelAdvisorRepository = AppModule.travelAdvisorRepository
                ) as T
            }
        }
    }
}
