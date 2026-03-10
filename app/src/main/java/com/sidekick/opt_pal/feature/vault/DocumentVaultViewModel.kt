package com.sidekick.opt_pal.feature.vault

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.documents.SecureDocumentIntakeUseCase
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class DocumentVaultUiState(
    val documents: List<DocumentMetadata> = emptyList(),
    val isLoading: Boolean = true,
    val showSecurityDialog: Boolean = false,
    val pendingUri: Uri? = null,
    val uploadError: String? = null,
    val uploadsInProgress: List<UploadProgress> = emptyList()
)

data class UploadProgress(
    val id: String,
    val fileName: String,
    val progress: Int = 0
)

class DocumentVaultViewModel(
    private val documentRepository: DocumentRepository,
    private val userSessionProvider: UserSessionProvider,
    private val secureDocumentIntakeUseCase: SecureDocumentIntakeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentVaultUiState())
    val uiState = _uiState.asStateFlow()

    private val uid: String?
        get() = userSessionProvider.currentUserId

    init {
        observeDocuments()
    }

    private fun observeDocuments() {
        val currentUid = uid ?: return
        documentRepository.getDocuments(currentUid)
            .onEach { docs ->
                _uiState.update { it.copy(documents = docs, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    fun onFileSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.update { it.copy(pendingUri = uri, showSecurityDialog = true) }
    }

    fun dismissSecurityDialog() {
        _uiState.update { it.copy(showSecurityDialog = false, pendingUri = null) }
    }

    fun confirmUpload(
        tag: String,
        consent: DocumentUploadConsent,
        contentResolver: ContentResolver
    ) {
        uid ?: return
        val fileUri = _uiState.value.pendingUri ?: return
        val fileName = secureDocumentIntakeUseCase.resolveFileName(fileUri, contentResolver)
        val validationError = secureDocumentIntakeUseCase.validateFile(fileUri, contentResolver)
        if (validationError != null) {
            _uiState.update {
                it.copy(
                    uploadError = validationError,
                    showSecurityDialog = false,
                    pendingUri = null
                )
            }
            return
        }
        val uploadId = UUID.randomUUID().toString()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showSecurityDialog = false,
                    pendingUri = null,
                    uploadsInProgress = it.uploadsInProgress + UploadProgress(uploadId, fileName, 0)
                )
            }
            val result = secureDocumentIntakeUseCase.uploadDocument(
                fileUri = fileUri,
                userTag = tag,
                consent = consent,
                contentResolver = contentResolver
            ) { bytesTransferred, totalBytes ->
                val percent = if (totalBytes > 0) {
                    ((bytesTransferred * 100) / totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                _uiState.update { state ->
                    state.copy(
                        uploadsInProgress = state.uploadsInProgress.map {
                            if (it.id == uploadId) it.copy(progress = percent) else it
                        }
                    )
                }
            }
            _uiState.update { state ->
                state.copy(
                    uploadsInProgress = state.uploadsInProgress.filterNot { it.id == uploadId }
                )
            }
            if (result.isSuccess) {
                AnalyticsLogger.logDocumentUploaded(tag)
            } else {
                _uiState.update {
                    it.copy(uploadError = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    fun clearUploadError() {
        _uiState.update { it.copy(uploadError = null) }
    }

    fun deleteDocument(document: DocumentMetadata) {
        val currentUid = uid ?: return
        viewModelScope.launch {
            val result = documentRepository.deleteDocument(currentUid, document)
            if (result.isSuccess) {
                AnalyticsLogger.logDocumentDeleted(document.id)
            } else {
                _uiState.update {
                    it.copy(uploadError = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    fun renameDocument(document: DocumentMetadata, newName: String) {
        val currentUid = uid ?: return
        viewModelScope.launch {
            val result = documentRepository.renameDocument(currentUid, document, newName)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(uploadError = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DocumentVaultViewModel(
                    AppModule.documentRepository,
                    AppModule.userSessionProvider,
                    AppModule.secureDocumentIntakeUseCase
                ) as T
            }
        }
    }
}
