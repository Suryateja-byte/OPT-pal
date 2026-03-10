package com.sidekick.opt_pal.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sidekick.opt_pal.core.calculations.calculateUnemploymentDays
import com.sidekick.opt_pal.core.calculations.toEmploymentPeriods
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.di.AppModule
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

class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val reportingRepository: ReportingRepository,
    private val documentRepository: com.sidekick.opt_pal.data.repository.DocumentRepository,
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
                    reportingRepository.getReportingObligations(user.uid)
                ) { profile, employments, reporting ->
                    buildDashboardState(profile, employments, reporting, timeProvider())
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
        viewModelScope.launch { authRepository.signOut() }
    }

    fun reprocessDocuments() {
        viewModelScope.launch {
            // We could show a loading state or toast here, but for now just fire and forget or log
            documentRepository.reprocessDocuments()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    AppModule.authRepository,
                    AppModule.dashboardRepository,
                    AppModule.reportingRepository,
                    AppModule.documentRepository
                )
            }
        }
    }
}

private fun buildDashboardState(
    profile: UserProfile?,
    employment: List<Employment>,
    reporting: List<ReportingObligation>,
    now: Long
): DashboardUiState {
    val sortedHistory = employment.sortedByDescending { it.startDate }
    val allowedDays = allowedUnemploymentDays(profile?.optType)
    val usedDays = profile?.optStartDate?.let {
        calculateUnemploymentDays(it, employment.toEmploymentPeriods(), now)
    } ?: 0
    val remaining = (allowedDays - usedDays).coerceAtLeast(0)
    val pendingReporting = reporting.filterNot { it.isCompleted }
    return DashboardUiState(
        isLoading = false,
        displayName = profile?.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() } ?: "Traveler",
        optLabel = formatOptLabel(profile?.optType),
        optStartDate = profile?.optStartDate,
        employmentHistory = sortedHistory,
        currentEmployment = sortedHistory.firstOrNull { it.endDate == null },
        unemploymentDaysAllowed = allowedDays,
        unemploymentDaysUsed = usedDays,
        daysRemaining = remaining,
        isOverLimit = usedDays >= allowedDays,
        pendingReportingCount = pendingReporting.size,
        nextReportingDue = pendingReporting.minByOrNull { it.dueDate }?.dueDate
    )
}

private fun allowedUnemploymentDays(optType: String?): Int {
    return when (optType?.lowercase()) {
        "stem" -> 150
        else -> 90
    }
}

private fun formatOptLabel(optType: String?): String {
    return when (optType?.lowercase()) {
        "stem" -> "STEM Extension"
        else -> "Initial 12-Month"
    }
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
    val pendingReportingCount: Int = 0,
    val nextReportingDue: Long? = null
) {
    val unemploymentProgress: Float
        get() = if (unemploymentDaysAllowed == 0) 0f else (unemploymentDaysUsed / unemploymentDaysAllowed.toFloat()).coerceIn(0f, 1f)
}
