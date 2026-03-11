package com.sidekick.opt_pal.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sidekick.opt_pal.core.calculations.UnemploymentAlertThreshold
import com.sidekick.opt_pal.core.calculations.UnemploymentDataQualityState
import com.sidekick.opt_pal.core.calculations.allowedUnemploymentDays
import com.sidekick.opt_pal.core.calculations.calculateUnemploymentForecast
import com.sidekick.opt_pal.core.calculations.utcStartOfDay
import com.sidekick.opt_pal.core.compliance.ComplianceScoreEngine
import com.sidekick.opt_pal.core.compliance.buildComplianceEvidenceSnapshot
import com.sidekick.opt_pal.core.pathway.VisaPathwayEngine
import com.sidekick.opt_pal.core.pathway.buildVisaPathwayEvidenceSnapshot
import com.sidekick.opt_pal.core.unemployment.UnemploymentAlertCoordinator
import com.sidekick.opt_pal.data.model.ComplianceHealthAvailability
import com.sidekick.opt_pal.data.model.ComplianceHealthScore
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.PolicyAlertAvailability
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertSeverity
import com.sidekick.opt_pal.data.model.PolicyAlertState
import com.sidekick.opt_pal.data.model.PeerDataSnapshot
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.UscisCaseStage
import com.sidekick.opt_pal.data.model.UscisCaseSummary
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerSummary
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.ComplianceHealthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.I983AssistantRepository
import com.sidekick.opt_pal.data.repository.PeerDataRepository
import com.sidekick.opt_pal.data.repository.PolicyAlertRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.data.repository.ScenarioSimulatorRepository
import com.sidekick.opt_pal.data.repository.VisaPathwayPlannerRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val reportingRepository: ReportingRepository,
    private val caseStatusRepository: CaseStatusRepository,
    private val policyAlertRepository: PolicyAlertRepository,
    private val documentRepository: com.sidekick.opt_pal.data.repository.DocumentRepository,
    private val complianceHealthRepository: ComplianceHealthRepository,
    private val unemploymentAlertCoordinator: UnemploymentAlertCoordinator? = null,
    private val visaPathwayPlannerRepository: VisaPathwayPlannerRepository? = null,
    private val i983AssistantRepository: I983AssistantRepository? = null,
    private val scenarioSimulatorRepository: ScenarioSimulatorRepository? = null,
    private val peerDataRepository: PeerDataRepository? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val currentUid = MutableStateFlow<String?>(null)
    private val policyAlertAvailability = MutableStateFlow(PolicyAlertAvailability())
    private val complianceAvailability = MutableStateFlow(ComplianceHealthAvailability())
    private val visaPathwayEntitlement = MutableStateFlow(false)
    private val peerDataEntitlement = MutableStateFlow(false)
    private val visaPathwayBundle = MutableStateFlow<VisaPathwayPlannerBundle?>(visaPathwayPlannerRepository?.getCachedBundle())
    private val peerDataSnapshot = MutableStateFlow<PeerDataSnapshot?>(peerDataRepository?.getCachedSnapshot())
    private val complianceScoreEngine = ComplianceScoreEngine(timeProvider)
    private val visaPathwayEngine = VisaPathwayEngine()
    private var lastPlannerEntitlementFlag: Boolean? = null
    private var lastPeerDataEntitlementFlag: Boolean? = null

    val uiState: StateFlow<DashboardUiState> = authRepository.getAuthState()
        .flatMapLatest { user ->
            currentUid.value = user?.uid
            if (user == null) {
                lastPlannerEntitlementFlag = null
                lastPeerDataEntitlementFlag = null
                visaPathwayEntitlement.value = false
                peerDataEntitlement.value = false
                peerDataSnapshot.value = peerDataRepository?.getCachedSnapshot()
                flowOf(DashboardUiState(isLoading = false))
            } else {
                viewModelScope.launch {
                    policyAlertAvailability.value = policyAlertRepository.resolveAvailability()
                        .getOrElse {
                            PolicyAlertAvailability(
                                isEnabled = false,
                                message = it.message ?: "Policy Alert Feed is unavailable."
                            )
                        }
                }
                viewModelScope.launch {
                    complianceAvailability.value = complianceHealthRepository.resolveAvailability()
                        .getOrElse {
                            ComplianceHealthAvailability(
                                isEnabled = false,
                                message = it.message ?: "Compliance Health Score is unavailable."
                            )
                        }
                }
                if (visaPathwayPlannerRepository != null) {
                    viewModelScope.launch {
                        visaPathwayBundle.value = visaPathwayPlannerRepository.getCachedBundle()
                        visaPathwayPlannerRepository.refreshBundle()
                            .onSuccess { visaPathwayBundle.value = it }
                    }
                }
                val dashboardCoreDependencies = combine(
                    authRepository.getUserProfile(user.uid),
                    dashboardRepository.getEmployments(user.uid),
                    reportingRepository.getReportingObligations(user.uid),
                    caseStatusRepository.observeTrackedCases(user.uid),
                    documentRepository.getDocuments(user.uid)
                ) { profile, employments, reporting, trackedCases, documents ->
                    DashboardCoreDependencies(
                        profile = profile,
                        employment = employments,
                        reporting = reporting,
                        trackedCases = trackedCases,
                        documents = documents
                    )
                }
                val dashboardDependencies = combine(
                    dashboardCoreDependencies,
                    policyAlertRepository.observePublishedAlerts(),
                    if (visaPathwayPlannerRepository != null) visaPathwayPlannerRepository.observeProfile(user.uid) else flowOf(null),
                    if (i983AssistantRepository != null) i983AssistantRepository.observeDrafts(user.uid) else flowOf(emptyList()),
                    if (scenarioSimulatorRepository != null) scenarioSimulatorRepository.observeDrafts(user.uid) else flowOf(emptyList())
                ) { core, policyAlerts, plannerProfile, i983Drafts, scenarioDrafts ->
                    DashboardDependencies(
                        profile = core.profile,
                        employment = core.employment,
                        reporting = core.reporting,
                        trackedCases = core.trackedCases,
                        documents = core.documents,
                        policyAlerts = policyAlerts,
                        plannerProfile = plannerProfile,
                        i983Drafts = i983Drafts,
                        scenarioDrafts = scenarioDrafts
                    )
                }
                val dashboardPolicyDependencies = combine(
                    dashboardDependencies,
                    policyAlertAvailability,
                    complianceAvailability,
                    policyAlertRepository.observeAlertStates(user.uid)
                ) { dependencies, alertAvailability, complianceHealthAvailability, policyStates ->
                    DashboardPolicyDependencies(
                        dependencies = dependencies,
                        alertAvailability = alertAvailability,
                        complianceAvailability = complianceHealthAvailability,
                        policyStates = policyStates
                    )
                }
                combine(
                    dashboardPolicyDependencies,
                    visaPathwayEntitlement,
                    visaPathwayBundle,
                    peerDataEntitlement,
                    peerDataSnapshot
                ) { combinedDependencies, plannerEnabled, plannerBundle, peerEnabled, latestPeerDataSnapshot ->
                    val dependencies = combinedDependencies.dependencies
                    val alertAvailability = combinedDependencies.alertAvailability
                    val complianceHealthAvailability = combinedDependencies.complianceAvailability
                    val policyStates = combinedDependencies.policyStates
                    val now = timeProvider()
                    maybeResolvePlannerEntitlement(dependencies.profile?.visaPathwayPlannerEnabled)
                    maybeResolvePeerDataEntitlement(dependencies.profile?.peerDataEnabled)
                    val complianceScore = if (complianceHealthAvailability.isEnabled) {
                        val evidence = buildComplianceEvidenceSnapshot(
                            profile = dependencies.profile,
                            employments = dependencies.employment,
                            reportingObligations = dependencies.reporting,
                            documents = dependencies.documents,
                            trackedCases = dependencies.trackedCases,
                            policyAlerts = dependencies.policyAlerts,
                            policyStates = policyStates,
                            now = now
                        )
                        val baseScore = complianceScoreEngine.score(
                            evidence = evidence,
                            now = now
                        )
                        val snapshotState = complianceHealthRepository.syncSnapshot(
                            uid = user.uid,
                            score = baseScore.score,
                            computedAt = baseScore.computedAt
                        )
                        baseScore.copy(delta = snapshotState.delta)
                    } else {
                        null
                    }
                    val visaPathwaySummary = if (plannerEnabled && plannerBundle != null) {
                        val snapshot = buildVisaPathwayEvidenceSnapshot(
                            profile = dependencies.profile,
                            plannerProfile = dependencies.plannerProfile,
                            employments = dependencies.employment,
                            reportingObligations = dependencies.reporting,
                            documents = dependencies.documents,
                            i983Drafts = dependencies.i983Drafts,
                            trackedCases = dependencies.trackedCases,
                            policyAlerts = dependencies.policyAlerts,
                            policyStates = policyStates,
                            bundle = plannerBundle
                        )
                        visaPathwayEngine.buildSummary(
                            assessments = visaPathwayEngine.assess(
                                evidence = snapshot,
                                bundle = plannerBundle,
                                now = now
                            ),
                            preferredPathwayId = dependencies.plannerProfile?.parsedPreferredPathwayId
                        )
                    } else {
                        null
                    }
                    val latestScenarioDraft = dependencies.scenarioDrafts
                        .filterNot { it.isArchived }
                        .maxByOrNull { draft ->
                            draft.lastOutcome?.computedAt ?: draft.lastRunAt ?: draft.updatedAt
                        }
                    buildDashboardState(
                        profile = dependencies.profile,
                        employment = dependencies.employment,
                        reporting = dependencies.reporting,
                        trackedCases = dependencies.trackedCases,
                        policyAlerts = dependencies.policyAlerts,
                        policyAlertAvailability = alertAvailability,
                        policyStates = policyStates,
                        complianceScore = complianceScore,
                        visaPathwaySummary = visaPathwaySummary,
                        peerDataSnapshot = latestPeerDataSnapshot.takeIf { peerEnabled },
                        latestScenarioDraft = latestScenarioDraft,
                        now = now
                    )
                }.onStart { emit(DashboardUiState(isLoading = true)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DashboardUiState()
        )

    fun deleteEmployment(employmentId: String) {
        val uid = currentUid.value ?: return
        viewModelScope.launch {
            dashboardRepository.deleteEmployment(uid, employmentId)
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            caseStatusRepository.syncMessagingEndpoint(enabled = false)
            authRepository.signOut()
        }
    }

    fun reprocessDocuments() {
        viewModelScope.launch {
            // We could show a loading state or toast here, but for now just fire and forget or log
            documentRepository.reprocessDocuments()
        }
    }

    fun updateUnemploymentTrackingStartDate(dateMillis: Long) {
        viewModelScope.launch {
            val result = authRepository.updateUnemploymentTrackingStartDate(dateMillis)
            if (result.isSuccess) {
                unemploymentAlertCoordinator?.syncForCurrentUser()
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    AppModule.authRepository,
                    AppModule.dashboardRepository,
                    AppModule.reportingRepository,
                    AppModule.caseStatusRepository,
                    AppModule.policyAlertRepository,
                    AppModule.documentRepository,
                    AppModule.complianceHealthRepository,
                    AppModule.unemploymentAlertCoordinator,
                    visaPathwayPlannerRepository = AppModule.visaPathwayPlannerRepository,
                    i983AssistantRepository = AppModule.i983AssistantRepository,
                    scenarioSimulatorRepository = AppModule.scenarioSimulatorRepository,
                    peerDataRepository = AppModule.peerDataRepository
                )
            }
        }
    }

    private fun maybeResolvePlannerEntitlement(userFlag: Boolean?) {
        if (visaPathwayPlannerRepository == null || userFlag == lastPlannerEntitlementFlag) return
        lastPlannerEntitlementFlag = userFlag
        viewModelScope.launch {
            visaPathwayEntitlement.value = visaPathwayPlannerRepository.resolveEntitlement(userFlag)
                .getOrNull()
                ?.isEnabled == true
        }
    }

    private fun maybeResolvePeerDataEntitlement(userFlag: Boolean?) {
        if (peerDataRepository == null || userFlag == lastPeerDataEntitlementFlag) return
        lastPeerDataEntitlementFlag = userFlag
        viewModelScope.launch {
            val enabled = peerDataRepository.resolveEntitlement(userFlag)
                .getOrNull()
                ?.isEnabled == true
            peerDataEntitlement.value = enabled
            if (enabled) {
                peerDataSnapshot.value = peerDataRepository.getCachedSnapshot()
                peerDataRepository.getPeerSnapshot()
                    .onSuccess { snapshot -> peerDataSnapshot.value = snapshot }
            } else {
                peerDataSnapshot.value = null
            }
        }
    }
}

private data class DashboardCoreDependencies(
    val profile: UserProfile?,
    val employment: List<Employment>,
    val reporting: List<ReportingObligation>,
    val trackedCases: List<UscisCaseTracker>,
    val documents: List<DocumentMetadata>
)

private data class DashboardDependencies(
    val profile: UserProfile?,
    val employment: List<Employment>,
    val reporting: List<ReportingObligation>,
    val trackedCases: List<UscisCaseTracker>,
    val documents: List<DocumentMetadata>,
    val policyAlerts: List<PolicyAlertCard>,
    val plannerProfile: com.sidekick.opt_pal.data.model.VisaPathwayProfile?,
    val i983Drafts: List<com.sidekick.opt_pal.data.model.I983Draft>,
    val scenarioDrafts: List<ScenarioDraft>
)

private data class DashboardPolicyDependencies(
    val dependencies: DashboardDependencies,
    val alertAvailability: PolicyAlertAvailability,
    val complianceAvailability: ComplianceHealthAvailability,
    val policyStates: List<PolicyAlertState>
)

private fun buildDashboardState(
    profile: UserProfile?,
    employment: List<Employment>,
    reporting: List<ReportingObligation>,
    trackedCases: List<UscisCaseTracker>,
    policyAlerts: List<PolicyAlertCard>,
    policyAlertAvailability: PolicyAlertAvailability,
    policyStates: List<PolicyAlertState>,
    complianceScore: ComplianceHealthScore?,
    visaPathwaySummary: VisaPathwayPlannerSummary?,
    peerDataSnapshot: PeerDataSnapshot?,
    latestScenarioDraft: ScenarioDraft?,
    now: Long
): DashboardUiState {
    val sortedHistory = employment.sortedByDescending { it.startDate }
    val forecast = calculateUnemploymentForecast(
        optType = profile?.optType,
        optStartDate = profile?.optStartDate,
        unemploymentTrackingStartDate = profile?.unemploymentTrackingStartDate,
        optEndDate = profile?.optEndDate,
        employments = employment,
        now = now
    )
    val pendingReporting = reporting.filterNot { it.isCompleted }
    val firstMissingHoursEmploymentId = sortedHistory.firstOrNull { it.hoursPerWeek == null }?.id
    val counterMessage = buildCounterMessage(profile, forecast)
    val uscisSummary = trackedCases
        .filterNot { it.isArchived }
        .firstOrNull()
        ?.toSummary(now)
    val visiblePolicyAlerts = policyAlerts.filter { alert ->
        when (alert.audience.lowercase()) {
            "initial_opt" -> profile?.optType?.lowercase() != "stem"
            "stem_opt" -> profile?.optType?.lowercase() == "stem"
            else -> true
        }
    }
    val latestCriticalPolicyAlert = visiblePolicyAlerts
        .filter { !it.isArchived && !it.isSuperseded && it.parsedSeverity == PolicyAlertSeverity.CRITICAL }
        .maxByOrNull { it.publishedAt }
    val unreadPolicyCount = if (policyAlertAvailability.isEnabled) {
        visiblePolicyAlerts.count { alert ->
            policyStates.none { it.alertId == alert.id && it.openedAt != null }
        }
    } else {
        0
    }
    val counterActionLabel = when (forecast.dataQualityState) {
        UnemploymentDataQualityState.NEEDS_HOURS_REVIEW -> "Review employment hours"
        UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START -> "Add original OPT start date"
        UnemploymentDataQualityState.READY -> null
    }
    val primaryPeerCard = peerDataSnapshot?.primaryBenchmarkCard
    val fallbackPeerCard = peerDataSnapshot?.primaryOfficialCard
    val peerCardTitle = primaryPeerCard?.title ?: fallbackPeerCard?.title
    val peerCardSummary = primaryPeerCard?.summary ?: fallbackPeerCard?.summary
    val peerCardSourceLabel = primaryPeerCard?.parsedSource?.label ?: fallbackPeerCard?.parsedSource?.label
    val peerCardSampleSizeBand = primaryPeerCard?.sampleSizeBand ?: fallbackPeerCard?.sampleSizeBand
    val peerCardCohortBasis = primaryPeerCard?.cohortBasis ?: fallbackPeerCard?.cohortBasis
    val peerCardModeLabel = when {
        peerDataSnapshot == null -> null
        primaryPeerCard != null -> "Personalized cohort"
        peerDataSnapshot.notEnoughSimilarPeers -> "Official-only fallback"
        else -> "Official context"
    }
    val latestScenarioOutcome = latestScenarioDraft?.lastOutcome
    return DashboardUiState(
        isLoading = false,
        displayName = profile?.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() } ?: "Traveler",
        optLabel = formatOptLabel(profile?.optType),
        optStartDate = profile?.optStartDate,
        employmentHistory = sortedHistory,
        currentEmployment = sortedHistory.firstOrNull { it.endDate == null },
        unemploymentDaysAllowed = forecast.allowedDays,
        unemploymentDaysUsed = forecast.usedDays,
        daysRemaining = forecast.remainingDays,
        isOverLimit = forecast.usedDays > forecast.allowedDays,
        pendingReportingCount = pendingReporting.size,
        nextReportingDue = pendingReporting.minByOrNull { it.dueDate }?.dueDate,
        clockRunningNow = forecast.clockRunningNow,
        currentGapStartDate = forecast.currentGapStartDate,
        projectedExceedDate = forecast.projectedExceedDate,
        currentThreshold = forecast.currentThreshold,
        dataQualityState = forecast.dataQualityState,
        unemploymentStatusMessage = counterMessage,
        unemploymentActionLabel = counterActionLabel,
        firstEmploymentMissingHoursId = firstMissingHoursEmploymentId,
        isEstimate = forecast.isEstimate,
        unemploymentTrackingStartDate = profile?.unemploymentTrackingStartDate,
        uscisCaseSummary = uscisSummary,
        complianceScore = complianceScore,
        visaPathwaySummary = visaPathwaySummary,
        peerDataTitle = peerCardTitle,
        peerDataSummary = peerCardSummary,
        peerDataSourceLabel = peerCardSourceLabel,
        peerDataSampleSizeBand = peerCardSampleSizeBand,
        peerDataCohortBasis = peerCardCohortBasis,
        peerDataModeLabel = peerCardModeLabel,
        latestScenarioDraftName = latestScenarioDraft?.name?.ifBlank { null },
        latestScenarioOutcomeLabel = latestScenarioOutcome?.parsedOutcome?.label,
        latestScenarioHeadline = latestScenarioOutcome?.headline?.ifBlank { null },
        latestScenarioConfidenceLabel = latestScenarioOutcome?.parsedConfidence?.label,
        policyAlertUnreadCount = unreadPolicyCount,
        latestCriticalPolicyAlertTitle = latestCriticalPolicyAlert?.title.takeIf { policyAlertAvailability.isEnabled },
        latestCriticalPolicyAlertId = latestCriticalPolicyAlert?.id.takeIf { policyAlertAvailability.isEnabled }
    )
}

private fun buildCounterMessage(
    profile: UserProfile?,
    forecast: com.sidekick.opt_pal.core.calculations.UnemploymentForecast
): String {
    return when (forecast.dataQualityState) {
        UnemploymentDataQualityState.NEEDS_HOURS_REVIEW -> {
            "Review employment hours to unlock predictive forecasting and alerts. Current unemployment count is shown as an estimate."
        }
        UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START -> {
            "Add your original post-completion OPT start date to calculate the full 150-day STEM limit accurately."
        }
        UnemploymentDataQualityState.READY -> buildReadyCounterMessage(profile, forecast)
    }
}

private fun buildReadyCounterMessage(
    profile: UserProfile?,
    forecast: com.sidekick.opt_pal.core.calculations.UnemploymentForecast
): String {
    if (forecast.currentThreshold == UnemploymentAlertThreshold.OVER_LIMIT) {
        return "You have exceeded the unemployment limit. Contact your DSO immediately."
    }
    val projectedText = forecast.projectedExceedDate?.let(::formatUtcDateLabel)
    val optEndBoundary = profile?.optEndDate?.let { utcStartOfDay(it) + 86_400_000L }
    if (forecast.clockRunningNow && projectedText != null &&
        optEndBoundary != null &&
        forecast.projectedExceedDate >= optEndBoundary
    ) {
        return "Clock running, but this gap does not exceed your recorded limit before your OPT end date."
    }
    if (forecast.clockRunningNow && projectedText != null) {
        val prefix = when (forecast.currentThreshold) {
            UnemploymentAlertThreshold.DAY_88 -> "Critical risk."
            UnemploymentAlertThreshold.DAY_85 -> "Severe risk."
            UnemploymentAlertThreshold.DAY_80 -> "High risk."
            UnemploymentAlertThreshold.DAY_75 -> "Elevated risk."
            UnemploymentAlertThreshold.DAY_60 -> "Heads up."
            else -> "Clock running."
        }
        return "$prefix If this gap continues, you exceed the limit on $projectedText."
    }
    return if (forecast.currentThreshold == UnemploymentAlertThreshold.NONE) {
        "Clock paused. Qualifying employment is covering your OPT status right now."
    } else {
        "Clock paused. You have already used ${forecast.usedDays} of ${forecast.allowedDays} unemployment days."
    }
}

private fun formatOptLabel(optType: String?): String {
    return when (optType?.lowercase()) {
        "stem" -> "STEM Extension"
        else -> "Initial 12-Month"
    }
}

private fun formatUtcDateLabel(millis: Long): String {
    val formatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
    formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return formatter.format(java.util.Date(millis))
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val optLabel: String = "",
    val optStartDate: Long? = null,
    val employmentHistory: List<Employment> = emptyList(),
    val currentEmployment: Employment? = null,
    val unemploymentDaysAllowed: Int = 90,
    val unemploymentDaysUsed: Int = 0,
    val daysRemaining: Int = 90,
    val isOverLimit: Boolean = false,
    val clockRunningNow: Boolean = false,
    val currentGapStartDate: Long? = null,
    val projectedExceedDate: Long? = null,
    val currentThreshold: UnemploymentAlertThreshold = UnemploymentAlertThreshold.NONE,
    val dataQualityState: UnemploymentDataQualityState = UnemploymentDataQualityState.READY,
    val unemploymentStatusMessage: String = "",
    val unemploymentActionLabel: String? = null,
    val firstEmploymentMissingHoursId: String? = null,
    val isEstimate: Boolean = false,
    val unemploymentTrackingStartDate: Long? = null,
    val uscisCaseSummary: UscisCaseSummary? = null,
    val complianceScore: ComplianceHealthScore? = null,
    val visaPathwaySummary: VisaPathwayPlannerSummary? = null,
    val peerDataTitle: String? = null,
    val peerDataSummary: String? = null,
    val peerDataSourceLabel: String? = null,
    val peerDataSampleSizeBand: String? = null,
    val peerDataCohortBasis: String? = null,
    val peerDataModeLabel: String? = null,
    val latestScenarioDraftName: String? = null,
    val latestScenarioOutcomeLabel: String? = null,
    val latestScenarioHeadline: String? = null,
    val latestScenarioConfidenceLabel: String? = null,
    val policyAlertUnreadCount: Int = 0,
    val latestCriticalPolicyAlertTitle: String? = null,
    val latestCriticalPolicyAlertId: String? = null,
    val pendingReportingCount: Int = 0,
    val nextReportingDue: Long? = null
) {
    val unemploymentProgress: Float
        get() = if (unemploymentDaysAllowed == 0) 0f else (unemploymentDaysUsed / unemploymentDaysAllowed.toFloat()).coerceIn(0f, 1f)
}

private fun UscisCaseTracker.toSummary(now: Long): UscisCaseSummary {
    val recentWindowMillis = 24 * 60 * 60 * 1000L
    return UscisCaseSummary(
        caseId = id,
        receiptNumber = receiptNumber,
        stage = parsedStage,
        statusText = officialStatusText,
        lastCheckedAt = lastCheckedAt,
        lastChangedAt = lastChangedAt,
        hasRecentChange = lastChangedAt >= now - recentWindowMillis
    )
}
