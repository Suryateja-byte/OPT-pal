package com.sidekick.opt_pal.feature.reporting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ReportingUiState(
    val pendingObligations: List<ReportingObligation> = emptyList(),
    val completedObligations: List<ReportingObligation> = emptyList(),
    val isLoading: Boolean = true
)

class ReportingViewModel(
    private val reportingRepository: ReportingRepository,
    private val userSessionProvider: UserSessionProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportingUiState())
    val uiState = _uiState.asStateFlow()

    private val uid: String?
        get() = userSessionProvider.currentUserId

    init {
        loadObligations()
    }

    private fun loadObligations() {
        val currentUid = uid ?: return
        reportingRepository.getReportingObligations(currentUid)
            .onEach { obligations ->
                _uiState.value = ReportingUiState(
                    pendingObligations = obligations.filterNot { it.isCompleted },
                    completedObligations = obligations.filter { it.isCompleted },
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)
    }

    fun toggleCompletion(obligation: ReportingObligation) {
        val currentUid = uid ?: return
        viewModelScope.launch {
            val result = reportingRepository.toggleObligationStatus(
                currentUid,
                obligation.id,
                !obligation.isCompleted
            )
            if (result.isSuccess && !obligation.isCompleted) {
                AnalyticsLogger.logReportingCompleted(obligation.id)
            }
        }
    }

    fun deleteObligation(obligation: ReportingObligation) {
        val currentUid = uid ?: return
        viewModelScope.launch {
            reportingRepository.deleteObligation(currentUid, obligation.id)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReportingViewModel(
                    AppModule.reportingRepository,
                    AppModule.userSessionProvider
                ) as T
            }
        }
    }
}
