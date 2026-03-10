package com.sidekick.opt_pal.feature.reporting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.ReportableEventType
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingSource
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MILLIS_IN_DAY = 86_400_000L

data class ReportingEditorUiState(
    val description: String = "",
    val selectedType: ReportableEventType = ReportableEventType.OTHER,
    val dueDate: Long = 0L,
    val eventDate: Long = 0L,
    val showDatePicker: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val onSaveComplete: Boolean = false,
    val editingObligationId: String? = null,
    val isCompleted: Boolean = false,
    val createdBy: String = ReportingSource.MANUAL.name
)

class ManageReportingViewModel(
    private val reportingRepository: ReportingRepository,
    private val userSessionProvider: UserSessionProvider,
    private val obligationId: String?,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReportingEditorUiState(
            dueDate = defaultDueDate(),
            eventDate = timeProvider(),
            editingObligationId = obligationId
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        obligationId?.let { loadObligation(it) }
    }

    private fun defaultDueDate(): Long = timeProvider() + MILLIS_IN_DAY * 10

    private fun loadObligation(obligationId: String) {
        val uid = userSessionProvider.currentUserId
        if (uid == null) {
            _uiState.update { it.copy(errorMessage = "User not logged in.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = reportingRepository.getObligation(uid, obligationId)
            result.onSuccess { obligation ->
                if (obligation == null) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Reminder not found.")
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            description = obligation.description,
                            selectedType = obligation.eventType.toReportableEvent(),
                            dueDate = obligation.dueDate,
                            eventDate = obligation.eventDate,
                            isCompleted = obligation.isCompleted,
                            editingObligationId = obligation.id,
                            createdBy = obligation.createdBy,
                            isLoading = false
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Unable to load reminder."
                    )
                }
            }
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onTypeSelected(type: ReportableEventType) {
        _uiState.update { it.copy(selectedType = type) }
    }

    fun onShowDatePicker() {
        _uiState.update { it.copy(showDatePicker = true) }
    }

    fun onDismissDatePicker() {
        _uiState.update { it.copy(showDatePicker = false) }
    }

    fun onDueDateSelected(millis: Long) {
        _uiState.update { it.copy(dueDate = millis, showDatePicker = false) }
    }

    fun saveReminder() {
        val uid = userSessionProvider.currentUserId
        if (uid == null) {
            _uiState.update { it.copy(errorMessage = "User not logged in.") }
            return
        }
        val state = _uiState.value
        val trimmedDescription = state.description.trim()
        if (trimmedDescription.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please describe the reminder.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val isEditing = state.editingObligationId != null
            val obligation = ReportingObligation(
                id = state.editingObligationId.orEmpty(),
                eventType = state.selectedType.name,
                description = trimmedDescription,
                eventDate = if (isEditing) state.eventDate else timeProvider(),
                dueDate = state.dueDate,
                isCompleted = state.isCompleted,
                createdBy = if (isEditing) state.createdBy else ReportingSource.MANUAL.name
            )
            val result = if (isEditing) {
                reportingRepository.updateObligation(uid, obligation)
            } else {
                reportingRepository.addObligation(uid, obligation)
            }
            if (result.isSuccess) {
                if (!isEditing) {
                    AnalyticsLogger.logManualReportingCreated(obligation.eventType)
                }
                _uiState.update { it.copy(isLoading = false, onSaveComplete = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to save reminder."
                    )
                }
            }
        }
    }

    fun onSaveHandled() {
        _uiState.update { it.copy(onSaveComplete = false) }
    }

    companion object {
        fun provideFactory(obligationId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ManageReportingViewModel(
                        AppModule.reportingRepository,
                        AppModule.userSessionProvider,
                        obligationId
                    ) as T
                }
            }
    }
}

private fun String.toReportableEvent(): ReportableEventType {
    return runCatching { ReportableEventType.valueOf(this) }.getOrDefault(ReportableEventType.OTHER)
}
