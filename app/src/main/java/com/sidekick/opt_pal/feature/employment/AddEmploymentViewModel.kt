package com.sidekick.opt_pal.feature.employment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.core.unemployment.UnemploymentAlertCoordinator
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.ReportableEventType
import com.sidekick.opt_pal.data.model.ReportingActionType
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingSource
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AddEmploymentUiState(
    val employerName: String = "",
    val jobTitle: String = "",
    val hoursPerWeek: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val isCurrentJob: Boolean = true,
    val showStartDatePicker: Boolean = false,
    val showEndDatePicker: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val onSaveComplete: Boolean = false,
    val editingEmploymentId: String? = null,
    val createdWizardId: String? = null
)

private const val REPORTING_DEADLINE_DAYS = 10
private const val MILLIS_IN_DAY = 86_400_000L

class AddEmploymentViewModel(
    private val dashboardRepository: DashboardRepository,
    private val reportingRepository: ReportingRepository,
    private val userSessionProvider: UserSessionProvider,
    private val unemploymentAlertCoordinator: UnemploymentAlertCoordinator? = null,
    private val employmentId: String? = null
) : ViewModel() {

    private var originalEmployment: Employment? = null

    private val _uiState = MutableStateFlow(
        AddEmploymentUiState(
            isLoading = employmentId != null,
            editingEmploymentId = employmentId
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        employmentId?.let { loadEmployment(it) }
    }

    private fun loadEmployment(employmentId: String) {
        val uid = userSessionProvider.currentUserId
        if (uid == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "User not logged in.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = dashboardRepository.getEmployment(uid, employmentId)
            result.onSuccess { employment ->
                if (employment == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Employment not found.",
                            editingEmploymentId = employmentId
                        )
                    }
                } else {
                    originalEmployment = employment
                    _uiState.update {
                        it.copy(
                            editingEmploymentId = employmentId,
                            employerName = employment.employerName,
                            jobTitle = employment.jobTitle,
                            hoursPerWeek = employment.hoursPerWeek?.toString().orEmpty(),
                            startDate = employment.startDate,
                            endDate = employment.endDate,
                            isCurrentJob = employment.endDate == null,
                            isLoading = false
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Unable to load employment.",
                        editingEmploymentId = employmentId
                    )
                }
            }
        }
    }

    fun onEmployerNameChange(value: String) {
        _uiState.update { it.copy(employerName = value) }
    }

    fun onJobTitleChange(value: String) {
        _uiState.update { it.copy(jobTitle = value) }
    }

    fun onHoursPerWeekChange(value: String) {
        val sanitized = value.filter(Char::isDigit).take(3)
        _uiState.update { it.copy(hoursPerWeek = sanitized) }
    }

    fun onIsCurrentJobChange(value: Boolean) {
        _uiState.update {
            it.copy(
                isCurrentJob = value,
                endDate = if (value) null else it.endDate
            )
        }
    }

    fun showStartDatePicker() {
        _uiState.update { it.copy(showStartDatePicker = true) }
    }

    fun showEndDatePicker() {
        _uiState.update { it.copy(showEndDatePicker = true) }
    }

    fun dismissPickers() {
        _uiState.update { it.copy(showStartDatePicker = false, showEndDatePicker = false) }
    }

    fun onStartDateSelected(millis: Long) {
        _uiState.update { it.copy(startDate = millis, showStartDatePicker = false) }
    }

    fun onEndDateSelected(millis: Long) {
        _uiState.update { it.copy(endDate = millis, showEndDatePicker = false) }
    }

    fun saveEmployment() {
        val state = _uiState.value
        val uid = userSessionProvider.currentUserId
        if (uid == null) {
            _uiState.update { it.copy(errorMessage = "User not logged in.") }
            return
        }
        if (state.employerName.isBlank() || state.jobTitle.isBlank() || state.startDate == null) {
            _uiState.update { it.copy(errorMessage = "Please fill in all fields.") }
            return
        }
        val parsedHours = state.hoursPerWeek.toIntOrNull()
        if (parsedHours == null || parsedHours !in 1..168) {
            _uiState.update { it.copy(errorMessage = "Enter valid hours per week (1-168).") }
            return
        }
        if (!state.isCurrentJob && state.endDate == null) {
            _uiState.update { it.copy(errorMessage = "Please select an end date for past employment.") }
            return
        }
        val editingId = state.editingEmploymentId
        val employment = Employment(
            id = editingId ?: UUID.randomUUID().toString(),
            employerName = state.employerName,
            jobTitle = state.jobTitle,
            hoursPerWeek = parsedHours,
            startDate = state.startDate,
            endDate = if (state.isCurrentJob) null else state.endDate
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = dashboardRepository.addEmployment(uid, employment)
            if (result.isSuccess) {
                val createdWizardId = when {
                    editingId == null -> createWizardForEvent(
                        uid = uid,
                        eventType = ReportingWizardEventType.NEW_EMPLOYER,
                        employment = employment,
                        eventDate = employment.startDate
                    )
                    originalEmployment?.endDate == null && employment.endDate != null -> createWizardForEvent(
                        uid = uid,
                        eventType = ReportingWizardEventType.EMPLOYMENT_ENDED,
                        employment = employment,
                        eventDate = employment.endDate ?: System.currentTimeMillis()
                    )
                    else -> null
                }
                unemploymentAlertCoordinator?.syncForCurrentUser()
                AnalyticsLogger.logEmploymentSaved(employment.employerName)
                _uiState.value = AddEmploymentUiState(
                    onSaveComplete = true,
                    createdWizardId = createdWizardId
                )
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    private suspend fun createWizardForEvent(
        uid: String,
        eventType: ReportingWizardEventType,
        employment: Employment,
        eventDate: Long
    ): String? {
        val result = reportingRepository.startWizard(
            uid = uid,
            eventType = eventType,
            relatedEmploymentId = employment.id,
            eventDate = eventDate
        )
        val startResult = result.getOrElse {
            createFallbackReportingTask(uid, eventType, employment, eventDate)
            null
        } ?: return null
        return startResult.wizardId
    }

    private suspend fun createFallbackReportingTask(
        uid: String,
        eventType: ReportingWizardEventType,
        employment: Employment,
        eventDate: Long
    ) {
        val dueDate = eventDate + REPORTING_DEADLINE_DAYS * MILLIS_IN_DAY
        val obligation = ReportingObligation(
            eventType = when (eventType) {
                ReportingWizardEventType.NEW_EMPLOYER -> ReportableEventType.NEW_EMPLOYER.name
                ReportingWizardEventType.EMPLOYMENT_ENDED -> ReportableEventType.EMPLOYER_ENDED.name
                ReportingWizardEventType.MATERIAL_CHANGE -> ReportableEventType.EMPLOYMENT_UPDATED.name
            },
            description = when (eventType) {
                ReportingWizardEventType.NEW_EMPLOYER -> "Report new employer: ${employment.employerName}"
                ReportingWizardEventType.EMPLOYMENT_ENDED -> "Report employment ended: ${employment.employerName}"
                ReportingWizardEventType.MATERIAL_CHANGE -> "Report material change: ${employment.employerName}"
            },
            eventDate = eventDate,
            dueDate = dueDate,
            createdBy = ReportingSource.AUTO.name,
            relatedEmploymentId = employment.id,
            actionType = ReportingActionType.MANUAL_ONLY.wireValue,
            sourceEventType = eventType.wireValue
        )
        reportingRepository.addObligation(uid, obligation)
    }

    companion object {
        fun provideFactory(employmentId: String? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AddEmploymentViewModel(
                        AppModule.dashboardRepository,
                        AppModule.reportingRepository,
                        AppModule.userSessionProvider,
                        AppModule.unemploymentAlertCoordinator,
                        employmentId
                    ) as T
                }
            }
    }
}
