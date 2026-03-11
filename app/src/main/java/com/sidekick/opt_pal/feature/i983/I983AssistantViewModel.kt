package com.sidekick.opt_pal.feature.i983

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.i983.I983ValidationEngine
import com.sidekick.opt_pal.core.i983.buildI983AutofillResult
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.I983Assessment
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983DraftStatus
import com.sidekick.opt_pal.data.model.I983EntitlementState
import com.sidekick.opt_pal.data.model.I983PolicyBundle
import com.sidekick.opt_pal.data.model.I983Readiness
import com.sidekick.opt_pal.data.model.I983ValidationIssue
import com.sidekick.opt_pal.data.model.I983WorkflowType
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.I983AssistantRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class I983TextField {
    STUDENT_NAME, STUDENT_EMAIL, SCHOOL_RECOMMENDING, SCHOOL_DEGREE_EARNED, SEVIS_SCHOOL_CODE,
    DSO_CONTACT, STUDENT_SEVIS_ID, QUALIFYING_MAJOR_CIP, DEGREE_LEVEL, EMPLOYER_AUTH_NUMBER,
    EMPLOYER_NAME, EMPLOYER_STREET, EMPLOYER_SUITE, EMPLOYER_WEBSITE, EMPLOYER_CITY, EMPLOYER_STATE,
    EMPLOYER_ZIP, EMPLOYER_EIN, FULL_TIME_EMPLOYEES, NAICS_CODE, HOURS_PER_WEEK, SALARY_AMOUNT,
    OTHER_COMPENSATION_1, OTHER_COMPENSATION_2, OTHER_COMPENSATION_3, OTHER_COMPENSATION_4,
    EMPLOYER_OFFICIAL_NAME_AND_TITLE, EMPLOYING_ORGANIZATION_NAME, SITE_NAME, SITE_ADDRESS,
    OFFICIAL_NAME, OFFICIAL_TITLE, OFFICIAL_EMAIL, OFFICIAL_PHONE, STUDENT_ROLE, GOALS_OBJECTIVES,
    EMPLOYER_OVERSIGHT, MEASURES_ASSESSMENTS, ADDITIONAL_REMARKS, ANNUAL_EVALUATION_TEXT, FINAL_EVALUATION_TEXT
}

enum class I983DateField {
    REQUESTED_START, REQUESTED_END, DEGREE_AWARDED, EMPLOYMENT_START, ANNUAL_FROM, ANNUAL_TO, FINAL_FROM, FINAL_TO
}

data class I983AssistantUiState(
    val isLoading: Boolean = true,
    val isRefreshingPolicy: Boolean = false,
    val isSaving: Boolean = false,
    val isGeneratingNarrative: Boolean = false,
    val isExporting: Boolean = false,
    val entitlement: I983EntitlementState = I983EntitlementState(),
    val policyBundle: I983PolicyBundle? = null,
    val profile: UserProfile? = null,
    val employments: List<Employment> = emptyList(),
    val obligations: List<ReportingObligation> = emptyList(),
    val documents: List<DocumentMetadata> = emptyList(),
    val drafts: List<I983Draft> = emptyList(),
    val selectedDraftId: String? = null,
    val selectedWorkflowType: I983WorkflowType = I983WorkflowType.INITIAL_STEM_EXTENSION,
    val selectedEmploymentId: String = "",
    val selectedObligationId: String = "",
    val draft: I983Draft? = null,
    val assessment: I983Assessment? = null,
    val sourceLabels: List<String> = emptyList(),
    val isDirty: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val selectedEmployment: Employment?
        get() = employments.firstOrNull { it.id == (draft?.linkedEmploymentId?.ifBlank { selectedEmploymentId } ?: selectedEmploymentId) }

    val selectedObligation: ReportingObligation?
        get() = obligations.firstOrNull { it.id == (draft?.linkedObligationId?.ifBlank { selectedObligationId } ?: selectedObligationId) }
}

class I983AssistantViewModel(
    private val authRepository: AuthRepository,
    private val dashboardRepository: DashboardRepository,
    private val reportingRepository: ReportingRepository,
    private val documentRepository: DocumentRepository,
    private val i983AssistantRepository: I983AssistantRepository,
    private val validationEngine: I983ValidationEngine = I983ValidationEngine(),
    private val initialDraftId: String?,
    private val initialObligationId: String?,
    private val initialEmploymentId: String?,
    private val initialWorkflowType: I983WorkflowType?,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        I983AssistantUiState(
            selectedDraftId = initialDraftId,
            selectedEmploymentId = initialEmploymentId.orEmpty(),
            selectedObligationId = initialObligationId.orEmpty(),
            selectedWorkflowType = initialWorkflowType ?: I983WorkflowType.INITIAL_STEM_EXTENSION
        )
    )
    val uiState = _uiState.asStateFlow()

    private var currentUid: String? = null
    private var observationJob: Job? = null
    private var lastEntitlementFlag: Boolean? = null
    private var lastAutofillIssues: List<I983ValidationIssue> = emptyList()

    init {
        authRepository.getAuthState().onEach { user ->
            currentUid = user?.uid
            lastEntitlementFlag = null
            if (user == null) {
                observationJob?.cancel()
                _uiState.value = I983AssistantUiState(isLoading = false, errorMessage = "User not logged in.")
            } else {
                loadPolicyBundle()
                observeUserData(user.uid)
            }
        }.launchIn(viewModelScope)
    }

    fun refreshPolicyBundle() {
        loadPolicyBundle(forceInfoMessage = true)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun selectDraft(draftId: String?) {
        val draft = draftId?.let { id -> _uiState.value.drafts.firstOrNull { it.id == id } }
        _uiState.value = _uiState.value.copy(
            selectedDraftId = draftId,
            draft = draft,
            selectedWorkflowType = draft?.parsedWorkflowType ?: _uiState.value.selectedWorkflowType,
            selectedEmploymentId = draft?.linkedEmploymentId ?: _uiState.value.selectedEmploymentId,
            selectedObligationId = draft?.linkedObligationId ?: _uiState.value.selectedObligationId,
            isDirty = false,
            errorMessage = null,
            infoMessage = null
        )
        recalculateAssessment()
    }

    fun selectWorkflowType(workflowType: I983WorkflowType) {
        if (_uiState.value.draft == null) {
            _uiState.value = _uiState.value.copy(selectedWorkflowType = workflowType)
        } else {
            updateDraft { copy(workflowType = workflowType.wireValue) }
        }
    }

    fun selectEmployment(employmentId: String) {
        if (_uiState.value.draft == null) _uiState.value = _uiState.value.copy(selectedEmploymentId = employmentId)
        else updateDraft { copy(linkedEmploymentId = employmentId) }
    }

    fun selectObligation(obligationId: String) {
        if (_uiState.value.draft == null) _uiState.value = _uiState.value.copy(selectedObligationId = obligationId)
        else updateDraft { copy(linkedObligationId = obligationId) }
    }

    fun toggleSelectedDocument(documentId: String) {
        updateDraft {
            val next = selectedDocumentIds.toMutableSet()
            if (!next.add(documentId)) next.remove(documentId)
            copy(selectedDocumentIds = next.toList())
        }
    }

    fun startDraft() {
        val uid = currentUid ?: return
        val state = _uiState.value
        val bundle = state.policyBundle
        if (!state.entitlement.isEnabled || bundle == null) {
            AnalyticsLogger.logI983EntitlementBlocked(state.entitlement.source.name)
            _uiState.value = state.copy(errorMessage = state.entitlement.message)
            return
        }
        val seed = buildI983AutofillResult(
            workflowType = state.selectedWorkflowType,
            templateVersion = bundle.templateVersion,
            policyVersion = bundle.version,
            linkedEmploymentId = state.selectedEmploymentId,
            linkedObligationId = state.selectedObligationId,
            profile = state.profile,
            employments = state.employments,
            obligations = state.obligations,
            existingDrafts = state.drafts,
            documents = state.documents,
            now = timeProvider()
        )
        lastAutofillIssues = seed.issues
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            i983AssistantRepository.createDraft(
                uid,
                seed.draft.copy(selectedDocumentIds = defaultSelectedDocumentIds(state.documents))
            ).onSuccess { draftId ->
                AnalyticsLogger.logI983DraftCreated(state.selectedWorkflowType.wireValue)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    selectedDraftId = draftId,
                    draft = seed.draft.copy(id = draftId, selectedDocumentIds = defaultSelectedDocumentIds(state.documents)),
                    sourceLabels = seed.sourceLabels,
                    isDirty = false,
                    infoMessage = "Draft created with autofill from your profile, employment records, and documents."
                )
                recalculateAssessment()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "Unable to create the I-983 draft."
                )
            }
        }
    }

    fun saveDraft() {
        viewModelScope.launch { persistCurrentDraft() }
    }

    fun generateNarrativeDraft() {
        val draft = _uiState.value.draft ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Start a draft before generating narrative text.")
            return
        }
        viewModelScope.launch {
            val persisted = persistCurrentDraft() ?: return@launch
            _uiState.value = _uiState.value.copy(isGeneratingNarrative = true, errorMessage = null)
            i983AssistantRepository.generateSectionDrafts(persisted.id, persisted.selectedDocumentIds)
                .onSuccess { narrative ->
                    val merged = persisted.copy(
                        generatedNarrative = narrative,
                        trainingPlanSection = persisted.trainingPlanSection.copy(
                            studentRole = persisted.trainingPlanSection.studentRole.ifBlank { narrative.studentRole },
                            goalsAndObjectives = persisted.trainingPlanSection.goalsAndObjectives.ifBlank { narrative.goalsAndObjectives },
                            employerOversight = persisted.trainingPlanSection.employerOversight.ifBlank { narrative.employerOversight },
                            measuresAndAssessments = persisted.trainingPlanSection.measuresAndAssessments.ifBlank { narrative.measuresAndAssessments }
                        ),
                        evaluationSection = persisted.evaluationSection.copy(
                            annualEvaluationText = persisted.evaluationSection.annualEvaluationText.ifBlank { narrative.annualEvaluation },
                            finalEvaluationText = persisted.evaluationSection.finalEvaluationText.ifBlank { narrative.finalEvaluation }
                        ),
                        status = if (narrative.parsedClassification == com.sidekick.opt_pal.data.model.I983NarrativeClassification.CONSULT_DSO_ATTORNEY) {
                            I983DraftStatus.ESCALATED.wireValue
                        } else {
                            persisted.status
                        }
                    )
                    saveUpdatedDraft(merged,
                        onSuccess = {
                            AnalyticsLogger.logI983NarrativeGenerated(narrative.parsedClassification.wireValue)
                            _uiState.value = _uiState.value.copy(
                                isGeneratingNarrative = false,
                                draft = merged,
                                isDirty = false,
                                infoMessage = if (narrative.parsedClassification == com.sidekick.opt_pal.data.model.I983NarrativeClassification.CONSULT_DSO_ATTORNEY) {
                                    "The draft generator flagged this case for DSO or attorney review."
                                } else {
                                    "Section 5 draft text generated."
                                }
                            )
                            recalculateAssessment()
                        },
                        onFailure = { message ->
                            _uiState.value = _uiState.value.copy(isGeneratingNarrative = false, errorMessage = message)
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingNarrative = false,
                        errorMessage = error.message ?: "Unable to generate narrative text."
                    )
                }
        }
    }

    fun exportOfficialPdf() {
        val state = _uiState.value
        if (state.assessment?.readiness != I983Readiness.READY_TO_EXPORT) {
            _uiState.value = state.copy(errorMessage = "Resolve current blockers before exporting the official PDF.")
            return
        }
        viewModelScope.launch {
            val persisted = persistCurrentDraft() ?: return@launch
            _uiState.value = _uiState.value.copy(isExporting = true, errorMessage = null)
            i983AssistantRepository.exportOfficialPdf(persisted.id)
                .onSuccess { result ->
                    val merged = persisted.copy(
                        latestExportDocumentId = result.documentId,
                        exportedAt = result.generatedAt,
                        templateVersion = result.templateVersion,
                        status = I983DraftStatus.EXPORTED.wireValue
                    )
                    saveUpdatedDraft(merged,
                        onSuccess = {
                            AnalyticsLogger.logI983Exported(result.templateVersion)
                            _uiState.value = _uiState.value.copy(
                                isExporting = false,
                                draft = merged,
                                isDirty = false,
                                infoMessage = "Official I-983 PDF exported to the secure vault."
                            )
                            recalculateAssessment()
                        },
                        onFailure = { message ->
                            _uiState.value = _uiState.value.copy(isExporting = false, errorMessage = message)
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = error.message ?: "Unable to export the official PDF."
                    )
                }
        }
    }

    fun linkSignedDocument(documentId: String) {
        val uid = currentUid ?: return
        val draft = _uiState.value.draft ?: return
        viewModelScope.launch {
            i983AssistantRepository.linkSignedDocument(uid, draft.id, documentId)
                .onSuccess {
                    AnalyticsLogger.logI983SignedDocumentLinked()
                    draft.linkedObligationId.takeIf(String::isNotBlank)?.let { reportingRepository.toggleObligationStatus(uid, it, true) }
                    _uiState.value = _uiState.value.copy(
                        draft = draft.copy(signedDocumentId = documentId, status = I983DraftStatus.SIGNED.wireValue),
                        infoMessage = "Signed I-983 linked and the related reporting item was marked complete."
                    )
                    recalculateAssessment()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to link the signed document.")
                }
        }
    }

    fun onTextFieldChanged(field: I983TextField, value: String) {
        updateDraft {
            when (field) {
                I983TextField.STUDENT_NAME -> copy(studentSection = studentSection.copy(studentName = value))
                I983TextField.STUDENT_EMAIL -> copy(studentSection = studentSection.copy(studentEmailAddress = value))
                I983TextField.SCHOOL_RECOMMENDING -> copy(studentSection = studentSection.copy(schoolRecommendingStemOpt = value))
                I983TextField.SCHOOL_DEGREE_EARNED -> copy(studentSection = studentSection.copy(schoolWhereDegreeWasEarned = value))
                I983TextField.SEVIS_SCHOOL_CODE -> copy(studentSection = studentSection.copy(sevisSchoolCode = value))
                I983TextField.DSO_CONTACT -> copy(studentSection = studentSection.copy(dsoNameAndContact = value))
                I983TextField.STUDENT_SEVIS_ID -> copy(studentSection = studentSection.copy(studentSevisId = value))
                I983TextField.QUALIFYING_MAJOR_CIP -> copy(studentSection = studentSection.copy(qualifyingMajorAndCipCode = value))
                I983TextField.DEGREE_LEVEL -> copy(studentSection = studentSection.copy(degreeLevel = value))
                I983TextField.EMPLOYER_AUTH_NUMBER -> copy(studentSection = studentSection.copy(employmentAuthorizationNumber = value))
                I983TextField.EMPLOYER_NAME -> copy(employerSection = employerSection.copy(employerName = value))
                I983TextField.EMPLOYER_STREET -> copy(employerSection = employerSection.copy(streetAddress = value))
                I983TextField.EMPLOYER_SUITE -> copy(employerSection = employerSection.copy(suite = value))
                I983TextField.EMPLOYER_WEBSITE -> copy(employerSection = employerSection.copy(employerWebsiteUrl = value))
                I983TextField.EMPLOYER_CITY -> copy(employerSection = employerSection.copy(city = value))
                I983TextField.EMPLOYER_STATE -> copy(employerSection = employerSection.copy(state = value))
                I983TextField.EMPLOYER_ZIP -> copy(employerSection = employerSection.copy(zipCode = value))
                I983TextField.EMPLOYER_EIN -> copy(employerSection = employerSection.copy(employerEin = value))
                I983TextField.FULL_TIME_EMPLOYEES -> copy(employerSection = employerSection.copy(fullTimeEmployeesInUs = value))
                I983TextField.NAICS_CODE -> copy(employerSection = employerSection.copy(naicsCode = value))
                I983TextField.HOURS_PER_WEEK -> copy(employerSection = employerSection.copy(hoursPerWeek = value.filter(Char::isDigit).toIntOrNull()))
                I983TextField.SALARY_AMOUNT -> copy(employerSection = employerSection.copy(salaryAmountAndFrequency = value))
                I983TextField.OTHER_COMPENSATION_1 -> copy(employerSection = employerSection.copy(otherCompensationLine1 = value))
                I983TextField.OTHER_COMPENSATION_2 -> copy(employerSection = employerSection.copy(otherCompensationLine2 = value))
                I983TextField.OTHER_COMPENSATION_3 -> copy(employerSection = employerSection.copy(otherCompensationLine3 = value))
                I983TextField.OTHER_COMPENSATION_4 -> copy(employerSection = employerSection.copy(otherCompensationLine4 = value))
                I983TextField.EMPLOYER_OFFICIAL_NAME_AND_TITLE -> copy(employerSection = employerSection.copy(employerOfficialNameAndTitle = value))
                I983TextField.EMPLOYING_ORGANIZATION_NAME -> copy(employerSection = employerSection.copy(employingOrganizationName = value))
                I983TextField.SITE_NAME -> copy(trainingPlanSection = trainingPlanSection.copy(siteName = value))
                I983TextField.SITE_ADDRESS -> copy(trainingPlanSection = trainingPlanSection.copy(siteAddress = value))
                I983TextField.OFFICIAL_NAME -> copy(trainingPlanSection = trainingPlanSection.copy(officialName = value))
                I983TextField.OFFICIAL_TITLE -> copy(trainingPlanSection = trainingPlanSection.copy(officialTitle = value))
                I983TextField.OFFICIAL_EMAIL -> copy(trainingPlanSection = trainingPlanSection.copy(officialEmail = value))
                I983TextField.OFFICIAL_PHONE -> copy(trainingPlanSection = trainingPlanSection.copy(officialPhoneNumber = value))
                I983TextField.STUDENT_ROLE -> copy(trainingPlanSection = trainingPlanSection.copy(studentRole = value))
                I983TextField.GOALS_OBJECTIVES -> copy(trainingPlanSection = trainingPlanSection.copy(goalsAndObjectives = value))
                I983TextField.EMPLOYER_OVERSIGHT -> copy(trainingPlanSection = trainingPlanSection.copy(employerOversight = value))
                I983TextField.MEASURES_ASSESSMENTS -> copy(trainingPlanSection = trainingPlanSection.copy(measuresAndAssessments = value))
                I983TextField.ADDITIONAL_REMARKS -> copy(trainingPlanSection = trainingPlanSection.copy(additionalRemarks = value))
                I983TextField.ANNUAL_EVALUATION_TEXT -> copy(evaluationSection = evaluationSection.copy(annualEvaluationText = value))
                I983TextField.FINAL_EVALUATION_TEXT -> copy(evaluationSection = evaluationSection.copy(finalEvaluationText = value))
            }
        }
    }

    fun onDateSelected(field: I983DateField, millis: Long?) {
        updateDraft {
            when (field) {
                I983DateField.REQUESTED_START -> copy(studentSection = studentSection.copy(requestedStartDate = millis))
                I983DateField.REQUESTED_END -> copy(studentSection = studentSection.copy(requestedEndDate = millis))
                I983DateField.DEGREE_AWARDED -> copy(studentSection = studentSection.copy(degreeAwardedDate = millis))
                I983DateField.EMPLOYMENT_START -> copy(employerSection = employerSection.copy(employmentStartDate = millis))
                I983DateField.ANNUAL_FROM -> copy(evaluationSection = evaluationSection.copy(annualEvaluationFromDate = millis))
                I983DateField.ANNUAL_TO -> copy(evaluationSection = evaluationSection.copy(annualEvaluationToDate = millis))
                I983DateField.FINAL_FROM -> copy(evaluationSection = evaluationSection.copy(finalEvaluationFromDate = millis))
                I983DateField.FINAL_TO -> copy(evaluationSection = evaluationSection.copy(finalEvaluationToDate = millis))
            }
        }
    }

    private fun observeUserData(uid: String) {
        observationJob?.cancel()
        observationJob = combine(
            authRepository.getUserProfile(uid),
            dashboardRepository.getEmployments(uid),
            reportingRepository.getReportingObligations(uid),
            documentRepository.getDocuments(uid),
            i983AssistantRepository.observeDrafts(uid)
        ) { profile, employments, obligations, documents, drafts ->
            ObservedI983Data(profile, employments, obligations, documents, drafts)
        }.onEach { observed ->
            maybeResolveEntitlement(observed.profile?.i983AssistantEnabled)
            val selectedId = when {
                !initialDraftId.isNullOrBlank() && observed.drafts.any { it.id == initialDraftId } -> initialDraftId
                !_uiState.value.selectedDraftId.isNullOrBlank() && observed.drafts.any { it.id == _uiState.value.selectedDraftId } -> _uiState.value.selectedDraftId
                else -> observed.drafts.firstOrNull()?.id
            }
            val remoteDraft = selectedId?.let { id -> observed.drafts.firstOrNull { it.id == id } }
            val localDraft = if (_uiState.value.isDirty && _uiState.value.draft?.id == remoteDraft?.id) _uiState.value.draft else remoteDraft
            val draft = localDraft?.let {
                if (it.selectedDocumentIds.isEmpty()) it.copy(selectedDocumentIds = defaultSelectedDocumentIds(observed.documents)) else it
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                profile = observed.profile,
                employments = observed.employments,
                obligations = observed.obligations,
                documents = observed.documents,
                drafts = observed.drafts,
                selectedDraftId = selectedId,
                selectedEmploymentId = draft?.linkedEmploymentId ?: _uiState.value.selectedEmploymentId.ifBlank { initialEmploymentId.orEmpty() },
                selectedObligationId = draft?.linkedObligationId ?: _uiState.value.selectedObligationId.ifBlank { initialObligationId.orEmpty() },
                selectedWorkflowType = draft?.parsedWorkflowType ?: _uiState.value.selectedWorkflowType,
                draft = draft,
                sourceLabels = _uiState.value.sourceLabels.ifEmpty {
                    observed.documents.take(4).map { document -> document.userTag.ifBlank { document.fileName } }
                }
            )
            recalculateAssessment()
        }.launchIn(viewModelScope)
    }

    private fun maybeResolveEntitlement(userFlag: Boolean?) {
        if (userFlag == lastEntitlementFlag) return
        lastEntitlementFlag = userFlag
        viewModelScope.launch {
            i983AssistantRepository.resolveEntitlement(userFlag)
                .onSuccess { entitlement ->
                    if (!entitlement.isEnabled) AnalyticsLogger.logI983EntitlementBlocked(entitlement.source.name)
                    _uiState.value = _uiState.value.copy(entitlement = entitlement)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        entitlement = I983EntitlementState(isEnabled = false, message = error.message ?: "Unable to resolve access.")
                    )
                }
        }
    }

    private fun loadPolicyBundle(forceInfoMessage: Boolean = false) {
        val cached = i983AssistantRepository.getCachedPolicyBundle()
        _uiState.value = _uiState.value.copy(
            policyBundle = cached ?: _uiState.value.policyBundle,
            isRefreshingPolicy = true,
            errorMessage = null,
            infoMessage = if (cached != null && forceInfoMessage) "Using the cached I-983 bundle while refreshing." else _uiState.value.infoMessage
        )
        viewModelScope.launch {
            i983AssistantRepository.refreshPolicyBundle()
                .onSuccess { bundle ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingPolicy = false,
                        policyBundle = bundle,
                        infoMessage = if (forceInfoMessage) "I-983 policy bundle refreshed." else _uiState.value.infoMessage
                    )
                    recalculateAssessment()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingPolicy = false,
                        errorMessage = if (_uiState.value.policyBundle == null) error.message ?: "Unable to load the I-983 policy bundle." else null,
                        infoMessage = if (_uiState.value.policyBundle != null) "Using the cached I-983 bundle because refresh failed." else _uiState.value.infoMessage
                    )
                }
        }
    }

    private suspend fun persistCurrentDraft(): I983Draft? {
        val uid = currentUid ?: return null
        val draft = _uiState.value.draft ?: return null
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        return if (draft.id.isBlank()) {
            i983AssistantRepository.createDraft(uid, draft).onSuccess { id ->
                _uiState.value = _uiState.value.copy(isSaving = false, draft = draft.copy(id = id), selectedDraftId = id, isDirty = false, infoMessage = "Draft saved.")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = error.message ?: "Unable to save the draft.")
            }.getOrNull()?.let { draft.copy(id = it) }
        } else {
            i983AssistantRepository.updateDraft(uid, draft).onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, infoMessage = "Draft saved.")
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = error.message ?: "Unable to save the draft.")
            }.getOrNull()?.let { draft }
        }
    }

    private fun saveUpdatedDraft(draft: I983Draft, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val uid = currentUid ?: return
        viewModelScope.launch {
            i983AssistantRepository.updateDraft(uid, draft)
                .onSuccess { onSuccess() }
                .onFailure { error -> onFailure(error.message ?: "Unable to update the draft.") }
        }
    }

    private fun updateDraft(transform: I983Draft.() -> I983Draft) {
        val draft = _uiState.value.draft ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "Start a draft before editing fields.")
            return
        }
        _uiState.value = _uiState.value.copy(draft = draft.transform(), isDirty = true, errorMessage = null, infoMessage = null)
        recalculateAssessment()
    }

    private fun recalculateAssessment() {
        val state = _uiState.value
        val draft = state.draft ?: return
        _uiState.value = state.copy(
            assessment = validationEngine.assess(
                draft = draft,
                policyBundle = state.policyBundle,
                profile = state.profile,
                linkedEmployment = state.employments.firstOrNull { it.id == draft.linkedEmploymentId },
                linkedObligation = state.obligations.firstOrNull { it.id == draft.linkedObligationId },
                autofillIssues = lastAutofillIssues,
                now = timeProvider()
            )
        )
    }

    private fun defaultSelectedDocumentIds(documents: List<DocumentMetadata>): List<String> {
        return documents.take(4).map { it.id }
    }

    companion object {
        fun provideFactory(
            draftId: String?,
            obligationId: String?,
            employmentId: String?,
            workflowType: I983WorkflowType?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return I983AssistantViewModel(
                    authRepository = AppModule.authRepository,
                    dashboardRepository = AppModule.dashboardRepository,
                    reportingRepository = AppModule.reportingRepository,
                    documentRepository = AppModule.documentRepository,
                    i983AssistantRepository = AppModule.i983AssistantRepository,
                    initialDraftId = draftId,
                    initialObligationId = obligationId,
                    initialEmploymentId = employmentId,
                    initialWorkflowType = workflowType
                ) as T
            }
        }
    }
}

private data class ObservedI983Data(
    val profile: UserProfile?,
    val employments: List<Employment>,
    val obligations: List<ReportingObligation>,
    val documents: List<DocumentMetadata>,
    val drafts: List<I983Draft>
)
