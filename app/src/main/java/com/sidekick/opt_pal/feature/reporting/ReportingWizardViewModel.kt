package com.sidekick.opt_pal.feature.reporting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.ReportingDraftClassification
import com.sidekick.opt_pal.data.model.ReportingDraftResult
import com.sidekick.opt_pal.data.model.ReportingWizard
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.model.ReportingWizardInput
import com.sidekick.opt_pal.data.model.ReportingWizardOptRegime
import com.sidekick.opt_pal.data.model.ReportingWizardStatus
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class ReportingWizardStep {
    EVENT,
    DETAILS,
    REVIEW,
    COMPLETE
}

data class ReportingWizardUiState(
    val isLoading: Boolean = true,
    val isPreparingWizard: Boolean = false,
    val isGeneratingDraft: Boolean = false,
    val isCompleting: Boolean = false,
    val wizardId: String? = null,
    val obligationId: String? = null,
    val step: ReportingWizardStep = ReportingWizardStep.EVENT,
    val selectedEventType: ReportingWizardEventType = ReportingWizardEventType.MATERIAL_CHANGE,
    val selectedEmploymentId: String? = null,
    val eventDate: Long? = null,
    val showEventDatePicker: Boolean = false,
    val profile: UserProfile? = null,
    val wizard: ReportingWizard? = null,
    val employments: List<Employment> = emptyList(),
    val availableDocuments: List<DocumentMetadata> = emptyList(),
    val selectedDocumentIds: Set<String> = emptySet(),
    val userInputs: ReportingWizardInput = ReportingWizardInput(),
    val editedDraft: String = "",
    val complianceWarning: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
) {
    val parsedOptRegime: ReportingWizardOptRegime
        get() = wizard?.parsedOptRegime
            ?: ReportingWizardOptRegime.fromOptType(profile?.optType)

    val generatedDraft: ReportingDraftResult?
        get() = wizard?.generatedDraft

    val canGenerateDraft: Boolean
        get() = wizardId != null && userInputs.jobDuties.trim().isNotBlank() && userInputs.majorName.trim().isNotBlank()

    val selectedEmployment: Employment?
        get() = employments.firstOrNull { it.id == selectedEmploymentId }
}

class ReportingWizardViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val documentRepository: DocumentRepository,
    private val reportingRepository: ReportingRepository,
    private val wizardIdArg: String?,
    private val obligationIdArg: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReportingWizardUiState(
            isLoading = wizardIdArg != null || obligationIdArg != null,
            wizardId = wizardIdArg,
            obligationId = obligationIdArg
        )
    )
    val uiState = _uiState.asStateFlow()

    private var currentUid: String? = null
    private var wizardObservationStarted = false

    init {
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        authRepository.getAuthState()
            .onEach { user ->
                currentUid = user?.uid
                if (user == null) {
                    _uiState.value = ReportingWizardUiState(isLoading = false, errorMessage = "User not logged in.")
                } else {
                    observeUserData(user.uid)
                    if (wizardIdArg != null) {
                        startWizardObservation(user.uid, wizardIdArg)
                    } else if (obligationIdArg != null) {
                        seedWizardFromObligation(user.uid, obligationIdArg)
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeUserData(uid: String) {
        combine(
            authRepository.getUserProfile(uid),
            dashboardRepository.getEmployments(uid),
            documentRepository.getDocuments(uid)
        ) { profile, employments, documents ->
            Triple(profile, employments, documents)
        }.onEach { (profile, employments, documents) ->
            val analyzedDocuments = documents.filter {
                DocumentProcessingMode.fromWireValue(it.processingMode) == DocumentProcessingMode.ANALYZE &&
                    it.processingStatus == "processed"
            }
            val currentState = _uiState.value
            val onboardingDocumentIds = profile?.onboardingDocumentIds.orEmpty().toSet()
            val defaultSelectedDocs = when {
                currentState.selectedDocumentIds.isNotEmpty() -> currentState.selectedDocumentIds
                onboardingDocumentIds.isNotEmpty() -> onboardingDocumentIds.intersect(analyzedDocuments.map { it.id }.toSet())
                else -> emptySet()
            }
            val currentInputs = currentState.userInputs
            val majorName = currentInputs.majorName.ifBlank { profile?.majorName.orEmpty() }
            val selectedEmployment = employments.firstOrNull { it.id == currentState.selectedEmploymentId }
            val updatedInputs = currentInputs.copy(
                majorName = majorName,
                employerName = currentInputs.employerName.ifBlank { selectedEmployment?.employerName.orEmpty() },
                jobTitle = currentInputs.jobTitle.ifBlank { selectedEmployment?.jobTitle.orEmpty() },
                hoursPerWeek = currentInputs.hoursPerWeek ?: selectedEmployment?.hoursPerWeek
            )
            _uiState.value = currentState.copy(
                isLoading = false,
                profile = profile,
                employments = employments,
                availableDocuments = analyzedDocuments,
                selectedDocumentIds = defaultSelectedDocs,
                userInputs = updatedInputs,
                complianceWarning = buildComplianceWarning(
                    optType = profile?.optType,
                    employments = employments,
                    selectedEmploymentId = currentState.selectedEmploymentId,
                    selectedHours = updatedInputs.hoursPerWeek
                )
            )
        }.launchIn(viewModelScope)
    }

    private fun seedWizardFromObligation(uid: String, obligationId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPreparingWizard = true, errorMessage = null)
            val result = reportingRepository.seedWizardFromObligation(uid, obligationId)
            result.onSuccess { start ->
                _uiState.value = _uiState.value.copy(
                    wizardId = start.wizardId,
                    obligationId = start.obligationId,
                    step = ReportingWizardStep.DETAILS,
                    isPreparingWizard = false
                )
                startWizardObservation(uid, start.wizardId)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isPreparingWizard = false,
                    errorMessage = throwable.message ?: "Unable to prepare reporting wizard."
                )
            }
        }
    }

    private fun startWizardObservation(uid: String, wizardId: String) {
        if (wizardObservationStarted) return
        wizardObservationStarted = true
        reportingRepository.getReportingWizard(uid, wizardId)
            .onEach { wizard ->
                if (wizard == null) return@onEach
                val nextStep = when (wizard.parsedStatus) {
                    ReportingWizardStatus.COMPLETED -> ReportingWizardStep.COMPLETE
                    else -> when {
                        wizard.generatedDraft != null || _uiState.value.step == ReportingWizardStep.REVIEW -> ReportingWizardStep.REVIEW
                        else -> ReportingWizardStep.DETAILS
                    }
                }
                _uiState.value = _uiState.value.copy(
                    wizard = wizard,
                    wizardId = wizard.id,
                    obligationId = wizard.obligationId.ifBlank { _uiState.value.obligationId },
                    step = nextStep,
                    userInputs = wizard.userInputs.mergeWith(_uiState.value.profile, _uiState.value.employments),
                    selectedEmploymentId = wizard.relatedEmploymentId.ifBlank { _uiState.value.selectedEmploymentId },
                    eventDate = if (wizard.eventDate == 0L) _uiState.value.eventDate else wizard.eventDate,
                    selectedEventType = wizard.parsedEventType,
                    editedDraft = _uiState.value.editedDraft.ifBlank {
                        wizard.editedDraft.ifBlank { wizard.generatedDraft?.draftParagraph.orEmpty() }
                    }
                )
            }
            .launchIn(viewModelScope)
    }

    fun onEventTypeSelected(eventType: ReportingWizardEventType) {
        _uiState.value = _uiState.value.copy(selectedEventType = eventType)
    }

    fun onEmploymentSelected(employmentId: String) {
        val employment = _uiState.value.employments.firstOrNull { it.id == employmentId }
        _uiState.value = _uiState.value.copy(
            selectedEmploymentId = employmentId,
            eventDate = defaultEventDate(_uiState.value.selectedEventType, employment),
            userInputs = _uiState.value.userInputs.copy(
                employerName = employment?.employerName.orEmpty(),
                jobTitle = employment?.jobTitle.orEmpty(),
                hoursPerWeek = employment?.hoursPerWeek
            ),
            complianceWarning = buildComplianceWarning(
                optType = _uiState.value.profile?.optType,
                employments = _uiState.value.employments,
                selectedEmploymentId = employmentId,
                selectedHours = employment?.hoursPerWeek
            )
        )
    }

    fun showEventDatePicker() {
        _uiState.value = _uiState.value.copy(showEventDatePicker = true)
    }

    fun dismissEventDatePicker() {
        _uiState.value = _uiState.value.copy(showEventDatePicker = false)
    }

    fun onEventDateSelected(dateMillis: Long) {
        _uiState.value = _uiState.value.copy(eventDate = dateMillis, showEventDatePicker = false)
    }

    fun startWizard() {
        val uid = currentUid ?: return
        val selectedEmploymentId = _uiState.value.selectedEmploymentId
        val eventDate = _uiState.value.eventDate
        if (selectedEmploymentId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Choose an employment record to continue.")
            return
        }
        if (eventDate == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Choose the date of the reporting event.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPreparingWizard = true, errorMessage = null)
            val result = reportingRepository.startWizard(
                uid = uid,
                eventType = _uiState.value.selectedEventType,
                relatedEmploymentId = selectedEmploymentId,
                eventDate = eventDate
            )
            result.onSuccess { start ->
                _uiState.value = _uiState.value.copy(
                    isPreparingWizard = false,
                    wizardId = start.wizardId,
                    obligationId = start.obligationId,
                    step = ReportingWizardStep.DETAILS
                )
                startWizardObservation(uid, start.wizardId)
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isPreparingWizard = false,
                    errorMessage = throwable.message ?: "Unable to start the reporting wizard."
                )
            }
        }
    }

    fun onEmployerNameChanged(value: String) = updateInputs { copy(employerName = value) }
    fun onJobTitleChanged(value: String) = updateInputs { copy(jobTitle = value) }
    fun onMajorNameChanged(value: String) = updateInputs { copy(majorName = value) }
    fun onWorksiteAddressChanged(value: String) = updateInputs { copy(worksiteAddress = value) }
    fun onSiteNameChanged(value: String) = updateInputs { copy(siteName = value) }
    fun onSupervisorNameChanged(value: String) = updateInputs { copy(supervisorName = value) }
    fun onSupervisorEmailChanged(value: String) = updateInputs { copy(supervisorEmail = value) }
    fun onSupervisorPhoneChanged(value: String) = updateInputs { copy(supervisorPhone = value) }
    fun onJobDutiesChanged(value: String) = updateInputs { copy(jobDuties = value) }
    fun onToolsAndSkillsChanged(value: String) = updateInputs { copy(toolsAndSkills = value) }
    fun onUserExplanationChanged(value: String) = updateInputs { copy(userExplanationNotes = value) }

    fun continueToReview() {
        val wizardId = _uiState.value.wizardId ?: return
        val uid = currentUid ?: return
        val inputs = _uiState.value.userInputs
        viewModelScope.launch {
            reportingRepository.updateWizardUserInputs(uid, wizardId, inputs)
            val majorName = inputs.majorName.trim()
            if (majorName.isNotBlank() && _uiState.value.profile?.majorName.isNullOrBlank()) {
                authRepository.updateMajorName(majorName)
            }
            _uiState.value = _uiState.value.copy(step = ReportingWizardStep.REVIEW)
        }
    }

    fun backToDetails() {
        _uiState.value = _uiState.value.copy(step = ReportingWizardStep.DETAILS)
    }

    fun onDocumentToggled(documentId: String) {
        val selected = _uiState.value.selectedDocumentIds.toMutableSet()
        if (!selected.add(documentId)) {
            selected.remove(documentId)
        }
        _uiState.value = _uiState.value.copy(selectedDocumentIds = selected)
    }

    fun generateDraft() {
        val wizardId = _uiState.value.wizardId ?: return
        val uid = currentUid ?: return
        viewModelScope.launch {
            val inputs = _uiState.value.userInputs
            reportingRepository.updateWizardUserInputs(uid, wizardId, inputs)
            val majorName = inputs.majorName.trim()
            if (majorName.isNotBlank() && _uiState.value.profile?.majorName.isNullOrBlank()) {
                authRepository.updateMajorName(majorName)
            }
            _uiState.value = _uiState.value.copy(isGeneratingDraft = true, errorMessage = null, infoMessage = null)
            val result = reportingRepository.generateRelationshipDraft(
                wizardId = wizardId,
                selectedDocumentIds = _uiState.value.selectedDocumentIds.toList()
            )
            result.onSuccess { draft ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingDraft = false,
                    wizard = _uiState.value.wizard?.copy(generatedDraft = draft),
                    editedDraft = _uiState.value.editedDraft.ifBlank { draft.draftParagraph },
                    errorMessage = null
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingDraft = false,
                    errorMessage = throwable.message ?: "Unable to generate the relationship-to-major draft."
                )
            }
        }
    }

    fun onEditedDraftChanged(value: String) {
        _uiState.value = _uiState.value.copy(editedDraft = value)
    }

    fun markDraftCopied() {
        val wizardId = _uiState.value.wizardId ?: return
        val uid = currentUid ?: return
        viewModelScope.launch {
            reportingRepository.updateWizardEditedDraft(uid, wizardId, _uiState.value.editedDraft)
            reportingRepository.markDraftCopied(uid, wizardId)
            _uiState.value = _uiState.value.copy(infoMessage = "Draft copied. Review it in the SEVP Portal before submitting.")
        }
    }

    fun completeWizard() {
        val wizardId = _uiState.value.wizardId ?: return
        val uid = currentUid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCompleting = true, errorMessage = null)
            reportingRepository.updateWizardEditedDraft(uid, wizardId, _uiState.value.editedDraft)
            val result = reportingRepository.completeWizard(uid, wizardId)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isCompleting = false,
                    step = ReportingWizardStep.COMPLETE,
                    infoMessage = "Wizard completed. Your reporting task has been marked done."
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isCompleting = false,
                    errorMessage = throwable.message ?: "Unable to complete the reporting wizard."
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null, errorMessage = null)
    }

    private fun updateInputs(transform: ReportingWizardInput.() -> ReportingWizardInput) {
        val updatedInputs = _uiState.value.userInputs.transform()
        _uiState.value = _uiState.value.copy(
            userInputs = updatedInputs,
            complianceWarning = buildComplianceWarning(
                optType = _uiState.value.profile?.optType,
                employments = _uiState.value.employments,
                selectedEmploymentId = _uiState.value.selectedEmploymentId,
                selectedHours = updatedInputs.hoursPerWeek
            )
        )
    }

    companion object {
        fun provideFactory(
            wizardId: String?,
            obligationId: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReportingWizardViewModel(
                    authRepository = AppModule.authRepository,
                    dashboardRepository = AppModule.dashboardRepository,
                    documentRepository = AppModule.documentRepository,
                    reportingRepository = AppModule.reportingRepository,
                    wizardIdArg = wizardId,
                    obligationIdArg = obligationId
                ) as T
            }
        }
    }
}

private fun ReportingWizardInput.mergeWith(
    profile: UserProfile?,
    employments: List<Employment>
): ReportingWizardInput {
    val linkedEmployment = employments.firstOrNull { it.employerName == employerName || it.jobTitle == jobTitle }
    return copy(
        majorName = majorName.ifBlank { profile?.majorName.orEmpty() },
        employerName = employerName.ifBlank { linkedEmployment?.employerName.orEmpty() },
        jobTitle = jobTitle.ifBlank { linkedEmployment?.jobTitle.orEmpty() },
        hoursPerWeek = hoursPerWeek ?: linkedEmployment?.hoursPerWeek
    )
}

private fun defaultEventDate(
    eventType: ReportingWizardEventType,
    employment: Employment?
): Long {
    return when (eventType) {
        ReportingWizardEventType.NEW_EMPLOYER -> employment?.startDate ?: System.currentTimeMillis()
        ReportingWizardEventType.EMPLOYMENT_ENDED -> employment?.endDate ?: System.currentTimeMillis()
        ReportingWizardEventType.MATERIAL_CHANGE -> System.currentTimeMillis()
    }
}

private fun buildComplianceWarning(
    optType: String?,
    employments: List<Employment>,
    selectedEmploymentId: String?,
    selectedHours: Int?
): String? {
    val hours = selectedHours ?: return null
    if (hours >= 20) {
        return null
    }
    if (optType.equals("stem", ignoreCase = true)) {
        return "STEM OPT employment generally must remain at least 20 hours per week per employer."
    }
    val otherCoverageHours = employments
        .filter { it.id != selectedEmploymentId && it.endDate == null }
        .sumOf { it.hoursPerWeek ?: 0 }
    return if (otherCoverageHours < 20) {
        "Jobs below 20 hours/week may not stop the unemployment clock unless you have other qualifying OPT employment."
    } else {
        null
    }
}
