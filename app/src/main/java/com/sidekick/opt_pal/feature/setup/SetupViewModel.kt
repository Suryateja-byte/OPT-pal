package com.sidekick.opt_pal.feature.setup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.documents.SecureDocumentIntakeUseCase
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.core.unemployment.UnemploymentAlertCoordinator
import com.sidekick.opt_pal.data.model.CompleteSetupRequest
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.model.OnboardingDocumentCandidate
import com.sidekick.opt_pal.data.model.OnboardingDocumentType
import com.sidekick.opt_pal.data.model.OnboardingField
import com.sidekick.opt_pal.data.model.OnboardingProfileDraft
import com.sidekick.opt_pal.data.model.OnboardingSource
import com.sidekick.opt_pal.data.model.OptType
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStage {
    SOURCE,
    REVIEW
}

enum class SetupDateField {
    START,
    END
}

data class ProcessingDocumentStatus(
    val documentId: String,
    val label: String,
    val status: String
)

data class SetupUiState(
    val stage: SetupStage = SetupStage.SOURCE,
    val draft: OnboardingProfileDraft = OnboardingProfileDraft(),
    val eligibleDocuments: List<OnboardingDocumentCandidate> = emptyList(),
    val selectedDocumentIds: Set<String> = emptySet(),
    val processingDocuments: List<ProcessingDocumentStatus> = emptyList(),
    val showVaultDocuments: Boolean = false,
    val showDatePickerFor: SetupDateField? = null,
    val showSecurityDialog: Boolean = false,
    val pendingUri: Uri? = null,
    val uploadProgress: Int? = null,
    val uploadLabel: String? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class SetupViewModel(
    private val authRepository: AuthRepository,
    private val documentRepository: DocumentRepository,
    private val userSessionProvider: UserSessionProvider,
    private val secureDocumentIntakeUseCase: SecureDocumentIntakeUseCase,
    private val unemploymentAlertCoordinator: UnemploymentAlertCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState = _uiState.asStateFlow()

    private var hasInitializedDocumentObservation = false
    private val knownDocumentIds = mutableSetOf<String>()
    private val pendingSetupDocumentIds = mutableSetOf<String>()
    private var awaitingSetupUpload = false

    private val currentUid: String?
        get() = userSessionProvider.currentUserId

    init {
        observeDocuments()
    }

    fun onScanDocumentRequested() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
        awaitingSetupUpload = true
    }

    fun onUploadFileSelected(uri: Uri?) {
        if (uri == null) return
        awaitingSetupUpload = true
        _uiState.update {
            it.copy(
                pendingUri = uri,
                showSecurityDialog = true,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun dismissSecurityDialog() {
        awaitingSetupUpload = false
        _uiState.update { it.copy(showSecurityDialog = false, pendingUri = null) }
    }

    fun confirmDocumentUpload(
        tag: String,
        consent: DocumentUploadConsent,
        contentResolver: ContentResolver
    ) {
        val fileUri = _uiState.value.pendingUri ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showSecurityDialog = false,
                    pendingUri = null,
                    uploadProgress = 0,
                    uploadLabel = tag.takeIf(String::isNotBlank),
                    errorMessage = null,
                    infoMessage = null
                )
            }
            val result = secureDocumentIntakeUseCase.uploadDocument(
                fileUri = fileUri,
                userTag = tag,
                consent = consent,
                contentResolver = contentResolver
            ) { bytesSent, totalBytes ->
                val percent = if (totalBytes > 0) {
                    ((bytesSent * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                _uiState.update { it.copy(uploadProgress = percent) }
            }

            if (result.isSuccess) {
                if (consent.processingMode == DocumentProcessingMode.STORAGE_ONLY) {
                    awaitingSetupUpload = false
                    moveToManualReview(
                        infoMessage = "Document stored securely. AI onboarding only works with “Upload and analyze”, so continue manually or choose an analyzed document."
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            uploadProgress = null,
                            uploadLabel = null,
                            infoMessage = "Document uploaded. We’ll move to review as soon as analysis finishes."
                        )
                    }
                }
            } else {
                awaitingSetupUpload = false
                _uiState.update {
                    it.copy(
                        uploadProgress = null,
                        uploadLabel = null,
                        errorMessage = result.exceptionOrNull()?.message ?: "Upload failed."
                    )
                }
            }
        }
    }

    fun showVaultDocuments() {
        _uiState.update { it.copy(showVaultDocuments = true, errorMessage = null, infoMessage = null) }
    }

    fun onDocumentCandidateToggled(candidate: OnboardingDocumentCandidate) {
        _uiState.update { state ->
            val updatedIds = state.selectedDocumentIds.toMutableSet()
            val conflictingIds = state.eligibleDocuments
                .filter { it.documentType == candidate.documentType && it.documentId != candidate.documentId }
                .map { it.documentId }
                .toSet()
            updatedIds.removeAll(conflictingIds)
            if (!updatedIds.add(candidate.documentId)) {
                updatedIds.remove(candidate.documentId)
            }
            state.copy(selectedDocumentIds = updatedIds)
        }
    }

    fun useSelectedDocuments() {
        val selectedCandidates = selectedCandidates()
        if (selectedCandidates.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one analyzed I-20 or EAD document.") }
            return
        }
        val draft = buildOnboardingDraft(selectedCandidates)
        _uiState.update {
            it.copy(
                stage = SetupStage.REVIEW,
                draft = draft,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun skipToManualSetup() {
        moveToManualReview()
    }

    fun backToSource() {
        _uiState.update {
            it.copy(
                stage = SetupStage.SOURCE,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onOptTypeSelected(optType: OptType) {
        _uiState.update { it.copy(draft = it.draft.copy(optType = optType), errorMessage = null) }
    }

    fun onSevisIdChanged(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(sevisId = value), errorMessage = null) }
    }

    fun onSchoolNameChanged(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(schoolName = value), errorMessage = null) }
    }

    fun onCipCodeChanged(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(cipCode = value), errorMessage = null) }
    }

    fun onMajorNameChanged(value: String) {
        _uiState.update { it.copy(draft = it.draft.copy(majorName = value), errorMessage = null) }
    }

    fun onShowDatePicker(field: SetupDateField) {
        _uiState.update { it.copy(showDatePickerFor = field, errorMessage = null) }
    }

    fun onDismissDatePicker() {
        _uiState.update { it.copy(showDatePickerFor = null) }
    }

    fun onDateSelected(dateMillis: Long) {
        _uiState.update { state ->
            val updatedDraft = when (state.showDatePickerFor) {
                SetupDateField.START -> state.draft.copy(optStartDate = dateMillis)
                SetupDateField.END -> state.draft.copy(optEndDate = dateMillis)
                null -> state.draft
            }
            state.copy(draft = updatedDraft, showDatePickerFor = null)
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    fun onSave() {
        val draft = _uiState.value.draft
        val selectedOptType = draft.optType
        val selectedStartDate = draft.optStartDate

        if (selectedOptType == null) {
            _uiState.update { it.copy(errorMessage = "Please choose your OPT type.") }
            return
        }
        if (selectedStartDate == null) {
            _uiState.update { it.copy(errorMessage = "Please select your OPT start date.") }
            return
        }
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, infoMessage = null) }
            val request = CompleteSetupRequest(
                optType = selectedOptType.value,
                optStartDate = selectedStartDate,
                optEndDate = draft.optEndDate,
                sevisId = draft.sevisId.trim().takeIf { it.isNotBlank() },
                schoolName = draft.schoolName.trim().takeIf { it.isNotBlank() },
                cipCode = draft.cipCode.trim().takeIf { it.isNotBlank() },
                majorName = draft.majorName.trim().takeIf { it.isNotBlank() },
                onboardingSource = draft.onboardingSource.wireValue,
                onboardingDocumentIds = draft.sourceDocumentIds
            )
            val result = authRepository.completeSetup(request)
            if (result.isSuccess) {
                unemploymentAlertCoordinator?.syncForCurrentUser()
                AnalyticsLogger.logSetupCompleted(selectedOptType.value)
            }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    private fun observeDocuments() {
        val uid = currentUid ?: return
        documentRepository.getDocuments(uid)
            .onEach(::handleDocumentUpdate)
            .launchIn(viewModelScope)
    }

    private fun handleDocumentUpdate(documents: List<DocumentMetadata>) {
        val eligibleCandidates = documents.mapNotNull { it.toOnboardingCandidate() }
            .sortedByDescending { it.processedAt ?: 0L }
        val processingDocuments = documents
            .filter {
                DocumentProcessingMode.fromWireValue(it.processingMode) == DocumentProcessingMode.ANALYZE &&
                    it.processingStatus.isNotBlank() &&
                    it.processingStatus != "processed" &&
                    it.processingStatus != "error"
            }
            .sortedByDescending { it.uploadedAt }
            .map {
                ProcessingDocumentStatus(
                    documentId = it.id,
                    label = it.userTag.ifBlank { it.fileName },
                    status = it.processingStatus.replace('_', ' ')
                )
            }

        if (!hasInitializedDocumentObservation) {
            hasInitializedDocumentObservation = true
            knownDocumentIds.addAll(documents.map { it.id })
            _uiState.update {
                it.copy(
                    eligibleDocuments = eligibleCandidates,
                    processingDocuments = processingDocuments
                )
            }
            return
        }

        val newDocuments = documents.filter { it.id !in knownDocumentIds }
        knownDocumentIds += documents.map { it.id }
        if (awaitingSetupUpload && newDocuments.isNotEmpty()) {
            pendingSetupDocumentIds += newDocuments.map { it.id }
        }

        val autoSelectedCandidates = eligibleCandidates.filter { it.documentId in pendingSetupDocumentIds }
        if (awaitingSetupUpload && autoSelectedCandidates.isNotEmpty()) {
            awaitingSetupUpload = false
            pendingSetupDocumentIds.clear()
            val selectedIds = autoSelectedCandidates.map { it.documentId }.toSet()
            _uiState.update {
                it.copy(
                    eligibleDocuments = eligibleCandidates,
                    processingDocuments = processingDocuments,
                    selectedDocumentIds = selectedIds,
                    stage = SetupStage.REVIEW,
                    draft = buildOnboardingDraft(autoSelectedCandidates),
                    showVaultDocuments = true,
                    infoMessage = null,
                    errorMessage = null
                )
            }
            return
        }

        if (awaitingSetupUpload && pendingSetupDocumentIds.isNotEmpty()) {
            val pendingDocs = documents.filter { it.id in pendingSetupDocumentIds }
            val storageOnlyDocs = pendingDocs.filter {
                DocumentProcessingMode.fromWireValue(it.processingMode) == DocumentProcessingMode.STORAGE_ONLY
            }
            if (storageOnlyDocs.isNotEmpty()) {
                awaitingSetupUpload = false
                pendingSetupDocumentIds.clear()
                moveToManualReview(
                    infoMessage = "Document stored securely. To autofill setup, use “Upload and analyze” or select an analyzed document from your vault."
                )
                return
            }

            if (pendingDocs.any { it.processingStatus == "error" }) {
                awaitingSetupUpload = false
                pendingSetupDocumentIds.clear()
                _uiState.update {
                    it.copy(
                        eligibleDocuments = eligibleCandidates,
                        processingDocuments = processingDocuments,
                        errorMessage = "Document analysis failed. Continue manually or choose a different document.",
                        infoMessage = null
                    )
                }
                return
            }
        }

        _uiState.update {
            val selectedIds = it.selectedDocumentIds.intersect(eligibleCandidates.map { candidate -> candidate.documentId }.toSet())
            it.copy(
                eligibleDocuments = eligibleCandidates,
                processingDocuments = processingDocuments,
                selectedDocumentIds = selectedIds
            )
        }
    }

    private fun selectedCandidates(): List<OnboardingDocumentCandidate> {
        val selectedIds = _uiState.value.selectedDocumentIds
        return _uiState.value.eligibleDocuments.filter { it.documentId in selectedIds }
    }

    private fun moveToManualReview(infoMessage: String? = null) {
        awaitingSetupUpload = false
        pendingSetupDocumentIds.clear()
        _uiState.update {
            it.copy(
                stage = SetupStage.REVIEW,
                draft = OnboardingProfileDraft(onboardingSource = OnboardingSource.MANUAL),
                uploadProgress = null,
                uploadLabel = null,
                infoMessage = infoMessage,
                errorMessage = null
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SetupViewModel(
                    authRepository = AppModule.authRepository,
                    documentRepository = AppModule.documentRepository,
                    userSessionProvider = AppModule.userSessionProvider,
                    secureDocumentIntakeUseCase = AppModule.secureDocumentIntakeUseCase,
                    unemploymentAlertCoordinator = AppModule.unemploymentAlertCoordinator
                ) as T
            }
        }
    }
}
