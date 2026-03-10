package com.sidekick.opt_pal.feature.scan

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ScanUiState(
    val isUploading: Boolean = false,
    val uploadError: String? = null,
    val uploadSuccess: Boolean = false,
    val pendingUri: Uri? = null,
    val showSecurityDialog: Boolean = false
)

class ScanViewModel(
    private val documentRepository: DocumentRepository,
    private val userSessionProvider: UserSessionProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState = _uiState.asStateFlow()

    fun onDocumentCaptured(uri: Uri) {
        _uiState.update {
            it.copy(
                pendingUri = uri,
                showSecurityDialog = true,
                uploadError = null,
                uploadSuccess = false
            )
        }
    }

    fun dismissPendingUpload() {
        cleanupPendingFile(_uiState.value.pendingUri)
        _uiState.update { it.copy(pendingUri = null, showSecurityDialog = false) }
    }

    fun uploadDocument(tag: String, consent: DocumentUploadConsent, contentResolver: ContentResolver) {
        val uid = userSessionProvider.currentUserId ?: return
        val uri = _uiState.value.pendingUri ?: return
        val fileName = "scan_${System.currentTimeMillis()}.jpg"
        
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    showSecurityDialog = false,
                    uploadError = null
                )
            }
            
            val result = documentRepository.uploadDocument(
                uid = uid,
                fileUri = uri,
                fileName = fileName,
                userTag = tag,
                consent = consent,
                contentResolver = contentResolver
            )

            if (result.isSuccess) {
                AnalyticsLogger.logDocumentUploaded(tag)
                cleanupPendingFile(uri)
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadSuccess = true,
                        pendingUri = null
                    )
                }
            } else {
                cleanupPendingFile(uri)
                _uiState.update { 
                    it.copy(
                        isUploading = false, 
                        uploadError = result.exceptionOrNull()?.message ?: "Upload failed",
                        showSecurityDialog = false,
                        pendingUri = null
                    ) 
                }
            }
        }
    }

    fun resetState() {
        cleanupPendingFile(_uiState.value.pendingUri)
        _uiState.update { ScanUiState() }
    }

    private fun cleanupPendingFile(uri: Uri?) {
        val path = uri?.takeIf { it.scheme == "file" }?.path ?: return
        File(path).delete()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ScanViewModel(
                    AppModule.documentRepository,
                    AppModule.userSessionProvider
                ) as T
            }
        }
    }
}
