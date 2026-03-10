package com.sidekick.opt_pal.feature.tax

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.documents.SecureDocumentIntakeUseCase
import com.sidekick.opt_pal.data.model.DocumentCategory
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.model.EmployerRefundOutcome
import com.sidekick.opt_pal.data.model.FicaEligibilityClassification
import com.sidekick.opt_pal.data.model.FicaEligibilityResult
import com.sidekick.opt_pal.data.model.FicaRefundCase
import com.sidekick.opt_pal.data.model.FicaRefundPacket
import com.sidekick.opt_pal.data.model.FicaUserTaxInputs
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.W2ExtractionDraft
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.FicaRefundRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class TaxRefundStep {
    W2_SOURCE,
    ELIGIBILITY,
    EMPLOYER_REFUND,
    IRS_PACKET,
    COMPLETE
}

data class FicaTaxRefundUiState(
    val isLoading: Boolean = true,
    val step: TaxRefundStep = TaxRefundStep.W2_SOURCE,
    val profile: UserProfile? = null,
    val availableW2Documents: List<W2ExtractionDraft> = emptyList(),
    val cases: List<FicaRefundCase> = emptyList(),
    val selectedCaseId: String? = null,
    val selectedW2DocumentId: String? = null,
    val showSecurityDialog: Boolean = false,
    val pendingUri: Uri? = null,
    val isUploading: Boolean = false,
    val uploadProgress: Int = 0,
    val isCreatingCase: Boolean = false,
    val isEvaluatingEligibility: Boolean = false,
    val isGeneratingEmployerPacket: Boolean = false,
    val isGeneratingIrsPacket: Boolean = false,
    val firstUsStudentTaxYearInput: String = "",
    val userInputs: FicaUserTaxInputs = FicaUserTaxInputs(),
    val fullSsn: String = "",
    val fullEmployerEin: String = "",
    val mailingAddress: String = "",
    val latestEligibilityResult: FicaEligibilityResult? = null,
    val latestEmployerPacket: FicaRefundPacket? = null,
    val latestIrsPacket: FicaRefundPacket? = null,
    val pendingOpenDocumentId: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
) {
    val selectedCase: FicaRefundCase?
        get() = cases.firstOrNull { it.id == selectedCaseId }

    val selectedW2Document: W2ExtractionDraft?
        get() {
            val resolvedDocumentId = selectedW2DocumentId ?: selectedCase?.w2DocumentId
            return availableW2Documents.firstOrNull { it.documentId == resolvedDocumentId }
        }

    val eligibilityResult: FicaEligibilityResult?
        get() = latestEligibilityResult ?: selectedCase?.eligibilityResult

    val employerPacket: FicaRefundPacket?
        get() = latestEmployerPacket ?: selectedCase?.employerPacket

    val irsPacket: FicaRefundPacket?
        get() = latestIrsPacket ?: selectedCase?.irsPacket

    val canUseSelectedW2: Boolean
        get() = selectedW2Document != null && !isCreatingCase

    val canEvaluateEligibility: Boolean
        get() = selectedCaseId != null && !isEvaluatingEligibility

    val canGenerateIrsPacket: Boolean
        get() = !isGeneratingIrsPacket &&
            selectedCase?.parsedEmployerOutcome in setOf(
                EmployerRefundOutcome.REFUSED,
                EmployerRefundOutcome.NO_RESPONSE
            ) &&
            fullSsn.filter(Char::isDigit).length == 9 &&
            fullEmployerEin.filter(Char::isDigit).length == 9 &&
            mailingAddress.isNotBlank()

    val shouldShowExistingCases: Boolean
        get() = cases.isNotEmpty()
}

class FicaTaxRefundViewModel(
    private val authRepository: AuthRepository,
    private val documentRepository: DocumentRepository,
    private val ficaRefundRepository: FicaRefundRepository,
    private val secureDocumentIntakeUseCase: SecureDocumentIntakeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FicaTaxRefundUiState())
    val uiState = _uiState.asStateFlow()

    private var currentUid: String? = null
    private var observationJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        authRepository.getAuthState()
            .onEach { user ->
                currentUid = user?.uid
                if (user == null) {
                    observationJob?.cancel()
                    _uiState.value = FicaTaxRefundUiState(
                        isLoading = false,
                        errorMessage = "User not logged in."
                    )
                } else {
                    observeUserData(user.uid)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeUserData(uid: String) {
        observationJob?.cancel()
        observationJob = combine(
            authRepository.getUserProfile(uid),
            documentRepository.getDocuments(uid),
            ficaRefundRepository.observeCases(uid)
        ) { profile, documents, taxCases ->
            Triple(profile, documents, taxCases)
        }.onEach { (profile, documents, taxCases) ->
            val w2Documents = documents.mapNotNull(DocumentMetadata::toW2ExtractionDraft)
            val currentState = _uiState.value
            val selectedCase = taxCases.firstOrNull { it.id == currentState.selectedCaseId }
            val selectedW2DocumentId = when {
                currentState.selectedW2DocumentId != null &&
                    w2Documents.any { it.documentId == currentState.selectedW2DocumentId } ->
                    currentState.selectedW2DocumentId
                selectedCase != null && w2Documents.any { it.documentId == selectedCase.w2DocumentId } ->
                    selectedCase.w2DocumentId
                else -> currentState.selectedW2DocumentId
            }
            val userInputs = mergeUserInputs(
                profile = profile,
                existingInputs = currentState.userInputs,
                selectedCase = selectedCase
            )
            val nextStep = when {
                selectedCase == null -> TaxRefundStep.W2_SOURCE
                currentState.step == TaxRefundStep.W2_SOURCE -> TaxRefundStep.W2_SOURCE
                else -> resolveStep(selectedCase, currentState)
            }
            _uiState.value = currentState.copy(
                isLoading = false,
                step = nextStep,
                profile = profile,
                availableW2Documents = w2Documents,
                cases = taxCases.sortedByDescending { it.updatedAt },
                selectedCaseId = selectedCase?.id,
                selectedW2DocumentId = selectedW2DocumentId,
                userInputs = userInputs,
                firstUsStudentTaxYearInput = currentState.firstUsStudentTaxYearInput.ifBlank {
                    (userInputs.firstUsStudentTaxYear ?: profile?.firstUsStudentTaxYear)?.toString().orEmpty()
                },
                mailingAddress = currentState.mailingAddress.ifBlank { userInputs.currentMailingAddress },
                latestEligibilityResult = currentState.latestEligibilityResult ?: selectedCase?.eligibilityResult,
                latestEmployerPacket = currentState.latestEmployerPacket ?: selectedCase?.employerPacket,
                latestIrsPacket = currentState.latestIrsPacket ?: selectedCase?.irsPacket
            )
        }.launchIn(viewModelScope)
    }

    fun onFileSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.value = _uiState.value.copy(
            pendingUri = uri,
            showSecurityDialog = true,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun dismissSecurityDialog() {
        _uiState.value = _uiState.value.copy(showSecurityDialog = false, pendingUri = null)
    }

    fun confirmUpload(tag: String, consent: DocumentUploadConsent, contentResolver: ContentResolver) {
        val fileUri = _uiState.value.pendingUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showSecurityDialog = false,
                pendingUri = null,
                isUploading = true,
                uploadProgress = 0,
                errorMessage = null,
                infoMessage = null
            )
            val result = secureDocumentIntakeUseCase.uploadDocument(
                fileUri = fileUri,
                userTag = tag,
                consent = consent,
                documentCategory = DocumentCategory.TAX_SENSITIVE,
                chatEligible = false,
                contentResolver = contentResolver
            ) { bytesSent, totalBytes ->
                val percent = if (totalBytes > 0) {
                    ((bytesSent * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                _uiState.value = _uiState.value.copy(uploadProgress = percent)
            }
            result.onSuccess {
                AnalyticsLogger.logScreenView("FicaW2Uploaded")
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = 0,
                    infoMessage = if (consent.processingMode == DocumentProcessingMode.ANALYZE) {
                        "W-2 uploaded securely. It will appear below after analysis completes."
                    } else {
                        "W-2 stored securely. Upload and analyze is required to continue with refund checking."
                    }
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadProgress = 0,
                    errorMessage = throwable.message ?: "Unable to upload this W-2 right now."
                )
            }
        }
    }

    fun selectExistingCase(caseId: String) {
        val selectedCase = _uiState.value.cases.firstOrNull { it.id == caseId } ?: return
        _uiState.value = _uiState.value.copy(
            selectedCaseId = caseId,
            selectedW2DocumentId = selectedCase.w2DocumentId,
            step = resolveStep(selectedCase),
            firstUsStudentTaxYearInput = selectedCase.userInputs.firstUsStudentTaxYear?.toString()
                ?: _uiState.value.firstUsStudentTaxYearInput,
            userInputs = mergeUserInputs(
                profile = _uiState.value.profile,
                existingInputs = _uiState.value.userInputs,
                selectedCase = selectedCase
            ),
            mailingAddress = _uiState.value.mailingAddress.ifBlank {
                selectedCase.userInputs.currentMailingAddress
            },
            latestEligibilityResult = selectedCase.eligibilityResult,
            latestEmployerPacket = selectedCase.employerPacket,
            latestIrsPacket = selectedCase.irsPacket,
            infoMessage = null,
            errorMessage = null
        )
    }

    fun beginNewCaseSelection() {
        _uiState.value = _uiState.value.copy(
            step = TaxRefundStep.W2_SOURCE,
            selectedCaseId = null,
            selectedW2DocumentId = null,
            firstUsStudentTaxYearInput = _uiState.value.profile?.firstUsStudentTaxYear?.toString().orEmpty(),
            userInputs = FicaUserTaxInputs(
                firstUsStudentTaxYear = _uiState.value.profile?.firstUsStudentTaxYear
            ),
            fullSsn = "",
            fullEmployerEin = "",
            mailingAddress = "",
            latestEligibilityResult = null,
            latestEmployerPacket = null,
            latestIrsPacket = null,
            infoMessage = null,
            errorMessage = null
        )
    }

    fun onW2Selected(documentId: String) {
        _uiState.value = _uiState.value.copy(
            selectedW2DocumentId = documentId,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun startCaseFromSelectedW2() {
        val uid = currentUid ?: return
        val selectedW2 = _uiState.value.selectedW2Document
        if (selectedW2 == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Choose an analyzed W-2 to continue.")
            return
        }
        val existingCase = _uiState.value.cases.firstOrNull { it.w2DocumentId == selectedW2.documentId }
        if (existingCase != null) {
            selectExistingCase(existingCase.id)
            _uiState.value = _uiState.value.copy(infoMessage = "Resumed your existing refund case for this W-2.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingCase = true, errorMessage = null, infoMessage = null)
            ficaRefundRepository.createCase(uid, selectedW2)
                .onSuccess { caseId ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingCase = false,
                        selectedCaseId = caseId,
                        step = TaxRefundStep.ELIGIBILITY,
                        userInputs = _uiState.value.userInputs.copy(
                            firstUsStudentTaxYear = _uiState.value.firstUsStudentTaxYearInput.toIntOrNull()
                                ?: _uiState.value.profile?.firstUsStudentTaxYear
                        )
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isCreatingCase = false,
                        errorMessage = throwable.message ?: "Unable to start a refund case from this W-2."
                    )
                }
        }
    }

    fun onFirstUsStudentTaxYearChanged(value: String) {
        val normalized = value.filter(Char::isDigit).take(4)
        _uiState.value = _uiState.value.copy(
            firstUsStudentTaxYearInput = normalized,
            userInputs = _uiState.value.userInputs.copy(firstUsStudentTaxYear = normalized.toIntOrNull())
        )
    }

    fun onAuthorizedEmploymentConfirmedChanged(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            userInputs = _uiState.value.userInputs.copy(authorizedEmploymentConfirmed = value)
        )
    }

    fun onMaintainedStudentStatusChanged(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            userInputs = _uiState.value.userInputs.copy(maintainedStudentStatusForEntireTaxYear = value)
        )
    }

    fun onNoResidencyStatusChangeChanged(value: Boolean) {
        _uiState.value = _uiState.value.copy(
            userInputs = _uiState.value.userInputs.copy(noResidencyStatusChangeConfirmed = value)
        )
    }

    fun evaluateEligibility() {
        val uid = currentUid ?: return
        val caseId = _uiState.value.selectedCaseId ?: return
        val year = _uiState.value.firstUsStudentTaxYearInput.toIntOrNull()
            ?: _uiState.value.profile?.firstUsStudentTaxYear
        if (year == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Enter your first U.S. student tax year before continuing."
            )
            return
        }
        val userInputs = _uiState.value.userInputs.copy(firstUsStudentTaxYear = year)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isEvaluatingEligibility = true,
                errorMessage = null,
                infoMessage = null
            )
            ficaRefundRepository.updateUserInputs(uid, caseId, userInputs)
            authRepository.updateFirstUsStudentTaxYear(year)
            ficaRefundRepository.evaluateEligibility(caseId)
                .onSuccess { eligibility ->
                    _uiState.value = _uiState.value.copy(
                        isEvaluatingEligibility = false,
                        userInputs = userInputs,
                        latestEligibilityResult = eligibility,
                        step = if (eligibility.parsedClassification == FicaEligibilityClassification.ELIGIBLE) {
                            TaxRefundStep.EMPLOYER_REFUND
                        } else {
                            TaxRefundStep.COMPLETE
                        },
                        infoMessage = if (eligibility.parsedClassification == FicaEligibilityClassification.ELIGIBLE) {
                            "Eligibility looks clear. Ask the employer to refund the withholding first."
                        } else {
                            "The app saved a guidance result for this W-2."
                        }
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isEvaluatingEligibility = false,
                        errorMessage = throwable.message ?: "Unable to evaluate this refund case."
                    )
                }
        }
    }

    fun generateEmployerPacket() {
        val caseId = _uiState.value.selectedCaseId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingEmployerPacket = true,
                errorMessage = null,
                infoMessage = null
            )
            ficaRefundRepository.generateEmployerPacket(caseId)
                .onSuccess { packet ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingEmployerPacket = false,
                        latestEmployerPacket = packet,
                        pendingOpenDocumentId = packet.documentId,
                        infoMessage = "Employer refund packet generated in your secure vault."
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingEmployerPacket = false,
                        errorMessage = throwable.message ?: "Unable to generate the employer packet."
                    )
                }
        }
    }

    fun updateEmployerOutcome(outcome: EmployerRefundOutcome) {
        val uid = currentUid ?: return
        val caseId = _uiState.value.selectedCaseId ?: return
        viewModelScope.launch {
            ficaRefundRepository.updateEmployerOutcome(uid, caseId, outcome)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        step = when (outcome) {
                            EmployerRefundOutcome.REFUNDED -> TaxRefundStep.COMPLETE
                            EmployerRefundOutcome.REFUSED,
                            EmployerRefundOutcome.NO_RESPONSE -> TaxRefundStep.IRS_PACKET
                            EmployerRefundOutcome.PROMISED_CORRECTION,
                            EmployerRefundOutcome.UNKNOWN -> TaxRefundStep.EMPLOYER_REFUND
                        },
                        infoMessage = when (outcome) {
                            EmployerRefundOutcome.REFUNDED ->
                                "Employer refunded the withholding. This case can stay closed in the app."
                            EmployerRefundOutcome.PROMISED_CORRECTION ->
                                "Keep this case open until the employer confirms the correction and refund."
                            EmployerRefundOutcome.REFUSED ->
                                "Employer refused the refund. You can now prepare the IRS claim packet."
                            EmployerRefundOutcome.NO_RESPONSE ->
                                "No employer response recorded. You can now prepare the IRS claim packet."
                            EmployerRefundOutcome.UNKNOWN -> null
                        },
                        errorMessage = null
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = throwable.message ?: "Unable to update the employer outcome."
                    )
                }
        }
    }

    fun onFullSsnChanged(value: String) {
        _uiState.value = _uiState.value.copy(fullSsn = value.filter(Char::isDigit).take(9))
    }

    fun onFullEmployerEinChanged(value: String) {
        _uiState.value = _uiState.value.copy(fullEmployerEin = value.filter(Char::isDigit).take(9))
    }

    fun onMailingAddressChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            mailingAddress = value,
            userInputs = _uiState.value.userInputs.copy(currentMailingAddress = value)
        )
    }

    fun generateIrsPacket() {
        val caseId = _uiState.value.selectedCaseId ?: return
        val fullSsn = _uiState.value.fullSsn
        val fullEmployerEin = _uiState.value.fullEmployerEin
        val mailingAddress = _uiState.value.mailingAddress.trim()
        if (fullSsn.length != 9 || fullEmployerEin.length != 9 || mailingAddress.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Enter the full SSN, full employer EIN, and current mailing address."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingIrsPacket = true,
                errorMessage = null,
                infoMessage = null
            )
            ficaRefundRepository.generateIrsPacket(
                caseId = caseId,
                fullSsn = fullSsn,
                fullEmployerEin = fullEmployerEin,
                mailingAddress = mailingAddress
            ).onSuccess { packet ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingIrsPacket = false,
                    latestIrsPacket = packet,
                    pendingOpenDocumentId = packet.documentId,
                    step = TaxRefundStep.COMPLETE,
                    infoMessage = "IRS claim packet generated in your secure vault."
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingIrsPacket = false,
                    errorMessage = throwable.message ?: "Unable to generate the IRS claim packet."
                )
            }
        }
    }

    fun openPendingDocumentHandled() {
        _uiState.value = _uiState.value.copy(pendingOpenDocumentId = null)
    }

    fun archiveSelectedCase() {
        val uid = currentUid ?: return
        val caseId = _uiState.value.selectedCaseId ?: return
        viewModelScope.launch {
            ficaRefundRepository.archiveCase(uid, caseId)
                .onSuccess {
                    beginNewCaseSelection()
                    _uiState.value = _uiState.value.copy(infoMessage = "Refund case archived.")
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = throwable.message ?: "Unable to archive this refund case."
                    )
                }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null, errorMessage = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FicaTaxRefundViewModel(
                    authRepository = AppModule.authRepository,
                    documentRepository = AppModule.documentRepository,
                    ficaRefundRepository = AppModule.ficaRefundRepository,
                    secureDocumentIntakeUseCase = AppModule.secureDocumentIntakeUseCase
                ) as T
            }
        }
    }
}

private fun mergeUserInputs(
    profile: UserProfile?,
    existingInputs: FicaUserTaxInputs,
    selectedCase: FicaRefundCase?
): FicaUserTaxInputs {
    val caseInputs = selectedCase?.userInputs ?: FicaUserTaxInputs()
    return existingInputs.copy(
        firstUsStudentTaxYear = existingInputs.firstUsStudentTaxYear
            ?: caseInputs.firstUsStudentTaxYear
            ?: profile?.firstUsStudentTaxYear,
        authorizedEmploymentConfirmed = existingInputs.authorizedEmploymentConfirmed || caseInputs.authorizedEmploymentConfirmed,
        maintainedStudentStatusForEntireTaxYear = existingInputs.maintainedStudentStatusForEntireTaxYear ||
            caseInputs.maintainedStudentStatusForEntireTaxYear,
        noResidencyStatusChangeConfirmed = existingInputs.noResidencyStatusChangeConfirmed ||
            caseInputs.noResidencyStatusChangeConfirmed,
        currentMailingAddress = existingInputs.currentMailingAddress.ifBlank {
            caseInputs.currentMailingAddress
        }
    )
}

private fun resolveStep(case: FicaRefundCase, state: FicaTaxRefundUiState? = null): TaxRefundStep {
    val localEligibility = state?.latestEligibilityResult?.parsedClassification
    if (state?.latestIrsPacket != null) {
        return TaxRefundStep.COMPLETE
    }
    if (localEligibility == FicaEligibilityClassification.MANUAL_REVIEW_REQUIRED ||
        localEligibility == FicaEligibilityClassification.NOT_APPLICABLE ||
        localEligibility == FicaEligibilityClassification.OUT_OF_SCOPE
    ) {
        return TaxRefundStep.COMPLETE
    }
    if (state?.step == TaxRefundStep.IRS_PACKET &&
        case.parsedEmployerOutcome in setOf(EmployerRefundOutcome.REFUSED, EmployerRefundOutcome.NO_RESPONSE)
    ) {
        return TaxRefundStep.IRS_PACKET
    }
    if (localEligibility == FicaEligibilityClassification.ELIGIBLE &&
        state?.step in setOf(TaxRefundStep.EMPLOYER_REFUND, TaxRefundStep.IRS_PACKET, TaxRefundStep.COMPLETE)
    ) {
        return when (state?.step) {
            TaxRefundStep.IRS_PACKET -> TaxRefundStep.IRS_PACKET
            TaxRefundStep.COMPLETE -> TaxRefundStep.COMPLETE
            else -> TaxRefundStep.EMPLOYER_REFUND
        }
    }
    return when {
        case.irsPacket != null || case.parsedStatus == com.sidekick.opt_pal.data.model.FicaRefundCaseStatus.IRS_PACKET_READY ||
            case.parsedStatus == com.sidekick.opt_pal.data.model.FicaRefundCaseStatus.CLOSED_REFUNDED ||
            case.parsedStatus == com.sidekick.opt_pal.data.model.FicaRefundCaseStatus.CLOSED_OUT_OF_SCOPE ->
            TaxRefundStep.COMPLETE
        case.parsedStatus == com.sidekick.opt_pal.data.model.FicaRefundCaseStatus.MANUAL_REVIEW_REQUIRED ->
            TaxRefundStep.COMPLETE
        case.parsedEmployerOutcome == EmployerRefundOutcome.REFUSED ||
            case.parsedEmployerOutcome == EmployerRefundOutcome.NO_RESPONSE ->
            TaxRefundStep.IRS_PACKET
        case.parsedStatus == com.sidekick.opt_pal.data.model.FicaRefundCaseStatus.EMPLOYER_OUTREACH ||
            case.parsedStatus == com.sidekick.opt_pal.data.model.FicaRefundCaseStatus.ELIGIBILITY_READY ->
            TaxRefundStep.EMPLOYER_REFUND
        else -> TaxRefundStep.ELIGIBILITY
    }
}
