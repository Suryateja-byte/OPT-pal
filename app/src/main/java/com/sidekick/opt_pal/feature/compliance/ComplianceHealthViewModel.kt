package com.sidekick.opt_pal.feature.compliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.compliance.ComplianceScoreEngine
import com.sidekick.opt_pal.core.compliance.buildComplianceEvidenceSnapshot
import com.sidekick.opt_pal.data.model.ComplianceHealthAvailability
import com.sidekick.opt_pal.data.model.ComplianceHealthScore
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.ComplianceHealthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.PolicyAlertRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ComplianceHealthUiState(
    val isLoading: Boolean = true,
    val availability: ComplianceHealthAvailability = ComplianceHealthAvailability(),
    val profile: UserProfile? = null,
    val score: ComplianceHealthScore? = null,
    val errorMessage: String? = null
)

class ComplianceHealthViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val reportingRepository: ReportingRepository,
    private val caseStatusRepository: CaseStatusRepository,
    private val policyAlertRepository: PolicyAlertRepository,
    private val documentRepository: DocumentRepository,
    private val complianceHealthRepository: ComplianceHealthRepository,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComplianceHealthUiState())
    val uiState = _uiState.asStateFlow()

    private val scoreEngine = ComplianceScoreEngine(timeProvider)
    private var currentUid: String? = null
    private var observationJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        authRepository.getAuthState()
            .onEach { user ->
                observationJob?.cancel()
                observationJob = null
                currentUid = user?.uid
                if (user == null) {
                    _uiState.value = ComplianceHealthUiState(
                        isLoading = false,
                        availability = ComplianceHealthAvailability(
                            isEnabled = false,
                            message = "User not logged in."
                        ),
                        errorMessage = "User not logged in."
                    )
                } else {
                    resolveAvailabilityAndObserve(user.uid)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun resolveAvailabilityAndObserve(uid: String) {
        viewModelScope.launch {
            complianceHealthRepository.resolveAvailability()
                .onSuccess { availability ->
                    if (!availability.isEnabled) {
                        _uiState.value = ComplianceHealthUiState(
                            isLoading = false,
                            availability = availability
                        )
                    } else {
                        observeCompliance(uid, availability)
                    }
                }
                .onFailure { throwable ->
                    _uiState.value = ComplianceHealthUiState(
                        isLoading = false,
                        availability = ComplianceHealthAvailability(
                            isEnabled = false,
                            message = throwable.message ?: "Unable to load Compliance Health Score."
                        ),
                        errorMessage = throwable.message ?: "Unable to load Compliance Health Score."
                    )
                }
        }
    }

    private fun observeCompliance(uid: String, availability: ComplianceHealthAvailability) {
        val coreInputs = combine(
            authRepository.getUserProfile(uid),
            dashboardRepository.getEmployments(uid),
            reportingRepository.getReportingObligations(uid),
            caseStatusRepository.observeTrackedCases(uid),
            documentRepository.getDocuments(uid)
        ) { profile, employments, reporting, trackedCases, documents ->
            ComplianceCoreInputs(
                profile = profile,
                employments = employments,
                reporting = reporting,
                trackedCases = trackedCases,
                documents = documents
            )
        }
        observationJob = combine(
            coreInputs,
            policyAlertRepository.observePublishedAlerts(),
            policyAlertRepository.observeAlertStates(uid)
        ) { core, policyAlerts, policyStates ->
            ComplianceInputs(
                profile = core.profile,
                employments = core.employments,
                reporting = core.reporting,
                trackedCases = core.trackedCases,
                documents = core.documents,
                policyAlerts = policyAlerts,
                policyStates = policyStates
            )
        }.onEach { inputs ->
            val now = timeProvider()
            val evidence = buildComplianceEvidenceSnapshot(
                profile = inputs.profile,
                employments = inputs.employments,
                reportingObligations = inputs.reporting,
                documents = inputs.documents,
                trackedCases = inputs.trackedCases,
                policyAlerts = inputs.policyAlerts,
                policyStates = inputs.policyStates,
                now = now
            )
            val baseScore = scoreEngine.score(evidence = evidence, now = now)
            val snapshotState = complianceHealthRepository.syncSnapshot(
                uid = uid,
                score = baseScore.score,
                computedAt = baseScore.computedAt
            )
            _uiState.value = ComplianceHealthUiState(
                isLoading = false,
                availability = availability,
                profile = inputs.profile,
                score = baseScore.copy(delta = snapshotState.delta)
            )
        }.launchIn(viewModelScope)
    }

    companion object {
        fun provideFactory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ComplianceHealthViewModel(
                        authRepository = AppModule.authRepository,
                        dashboardRepository = AppModule.dashboardRepository,
                        reportingRepository = AppModule.reportingRepository,
                        caseStatusRepository = AppModule.caseStatusRepository,
                        policyAlertRepository = AppModule.policyAlertRepository,
                        documentRepository = AppModule.documentRepository,
                        complianceHealthRepository = AppModule.complianceHealthRepository
                    ) as T
                }
            }
    }
}

private data class ComplianceCoreInputs(
    val profile: UserProfile?,
    val employments: List<com.sidekick.opt_pal.data.model.Employment>,
    val reporting: List<com.sidekick.opt_pal.data.model.ReportingObligation>,
    val trackedCases: List<com.sidekick.opt_pal.data.model.UscisCaseTracker>,
    val documents: List<com.sidekick.opt_pal.data.model.DocumentMetadata>
)

private data class ComplianceInputs(
    val profile: UserProfile?,
    val employments: List<com.sidekick.opt_pal.data.model.Employment>,
    val reporting: List<com.sidekick.opt_pal.data.model.ReportingObligation>,
    val trackedCases: List<com.sidekick.opt_pal.data.model.UscisCaseTracker>,
    val documents: List<com.sidekick.opt_pal.data.model.DocumentMetadata>,
    val policyAlerts: List<com.sidekick.opt_pal.data.model.PolicyAlertCard>,
    val policyStates: List<com.sidekick.opt_pal.data.model.PolicyAlertState>
)
