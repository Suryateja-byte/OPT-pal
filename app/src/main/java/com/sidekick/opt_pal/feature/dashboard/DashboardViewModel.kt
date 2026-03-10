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
import com.sidekick.opt_pal.core.unemployment.UnemploymentAlertCoordinator
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UscisCaseStage
import com.sidekick.opt_pal.data.model.UscisCaseSummary
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
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
    private val documentRepository: com.sidekick.opt_pal.data.repository.DocumentRepository,
    private val unemploymentAlertCoordinator: UnemploymentAlertCoordinator? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val currentUid = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardUiState> = authRepository.getAuthState()
        .flatMapLatest { user ->
            currentUid.value = user?.uid
            if (user == null) {
                flowOf(DashboardUiState(isLoading = false))
            } else {
                combine(
                    authRepository.getUserProfile(user.uid),
                    dashboardRepository.getEmployments(user.uid),
                    reportingRepository.getReportingObligations(user.uid),
                    caseStatusRepository.observeTrackedCases(user.uid)
                ) { profile, employments, reporting, trackedCases ->
                    buildDashboardState(profile, employments, reporting, trackedCases, timeProvider())
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
                    AppModule.documentRepository,
                    AppModule.unemploymentAlertCoordinator
                )
            }
        }
    }
}

private fun buildDashboardState(
    profile: UserProfile?,
    employment: List<Employment>,
    reporting: List<ReportingObligation>,
    trackedCases: List<UscisCaseTracker>,
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
    val counterActionLabel = when (forecast.dataQualityState) {
        UnemploymentDataQualityState.NEEDS_HOURS_REVIEW -> "Review employment hours"
        UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START -> "Add original OPT start date"
        UnemploymentDataQualityState.READY -> null
    }
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
        uscisCaseSummary = uscisSummary
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
