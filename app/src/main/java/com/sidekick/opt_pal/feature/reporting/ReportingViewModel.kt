package com.sidekick.opt_pal.feature.reporting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.ReportingActionType
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingSource
import com.sidekick.opt_pal.data.model.ReportingWizard
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ReportingActionItem(
    val obligation: ReportingObligation,
    val wizard: ReportingWizard?
)

data class ReportingUiState(
    val actionItems: List<ReportingActionItem> = emptyList(),
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
        combine(
            reportingRepository.getReportingObligations(currentUid),
            reportingRepository.getReportingWizards(currentUid)
        ) { obligations, wizards ->
            val wizardById = wizards.associateBy { it.id }
            val actionItems = obligations
                .filterNot { it.isCompleted }
                .filter(::isWizardBackedObligation)
                .sortedBy { it.dueDate }
                .map { obligation ->
                    ReportingActionItem(
                        obligation = obligation,
                        wizard = obligation.wizardId.takeIf(String::isNotBlank)?.let(wizardById::get)
                    )
                }
            val manualPending = obligations
                .filterNot { it.isCompleted }
                .filterNot(::isWizardBackedObligation)
            ReportingUiState(
                actionItems = actionItems,
                pendingObligations = manualPending,
                completedObligations = obligations.filter { it.isCompleted },
                isLoading = false
            )
        }.onEach { state ->
            _uiState.value = state
        }
            .launchIn(viewModelScope)
    }

    fun toggleCompletion(obligation: ReportingObligation) {
        val currentUid = uid ?: return
        viewModelScope.launch {
            val result = if (!obligation.isCompleted && obligation.wizardId.isNotBlank()) {
                reportingRepository.completeWizard(currentUid, obligation.wizardId)
            } else {
                reportingRepository.toggleObligationStatus(
                    currentUid,
                    obligation.id,
                    !obligation.isCompleted
                )
            }
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

    private fun isWizardBackedObligation(obligation: ReportingObligation): Boolean {
        if (ReportingActionType.fromWireValue(obligation.actionType) == ReportingActionType.OPEN_WIZARD) {
            return true
        }
        return obligation.createdBy == ReportingSource.AUTO.name &&
            supportedWizardEvents.contains(obligation.eventType)
    }

    companion object {
        private val supportedWizardEvents = setOf(
            com.sidekick.opt_pal.data.model.ReportableEventType.NEW_EMPLOYER.name,
            com.sidekick.opt_pal.data.model.ReportableEventType.EMPLOYER_ENDED.name,
            com.sidekick.opt_pal.data.model.ReportableEventType.EMPLOYMENT_UPDATED.name
        )

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
