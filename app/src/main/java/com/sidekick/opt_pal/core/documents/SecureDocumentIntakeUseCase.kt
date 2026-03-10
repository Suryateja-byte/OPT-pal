package com.sidekick.opt_pal.core.documents

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.sidekick.opt_pal.data.model.DocumentCategory
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.feature.vault.FileNameResolver
import java.util.Locale

private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024L
private val ALLOWED_MIME_TYPES = setOf(
    "application/pdf",
    "image/jpeg",
    "image/png",
    "image/heic",
    "image/heif"
)

data class SecureDocumentIntakeResult(
    val fileName: String
)

class SecureDocumentIntakeUseCase(
    private val documentRepository: DocumentRepository,
    private val userSessionProvider: UserSessionProvider,
    private val fileNameResolver: FileNameResolver
) {

    fun validateFile(uri: Uri, contentResolver: ContentResolver): String? {
        val mimeType = contentResolver.getType(uri)?.lowercase(Locale.US)
        val isAllowedMime = mimeType != null && (mimeType in ALLOWED_MIME_TYPES || mimeType.startsWith("image/"))
        if (!isAllowedMime) {
            return "Only PDF or image files are supported."
        }
        val fileSize = queryFileSize(uri, contentResolver)
        if (fileSize != null && fileSize > MAX_UPLOAD_BYTES) {
            return "File is larger than 10 MB."
        }
        return null
    }

    fun resolveFileName(uri: Uri, contentResolver: ContentResolver): String {
        return fileNameResolver.resolve(uri, contentResolver)
    }

    suspend fun uploadDocument(
        fileUri: Uri,
        userTag: String,
        consent: DocumentUploadConsent,
        documentCategory: DocumentCategory = DocumentCategory.GENERAL,
        chatEligible: Boolean? = null,
        contentResolver: ContentResolver,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<SecureDocumentIntakeResult> {
        val currentUid = userSessionProvider.currentUserId
            ?: return Result.failure(IllegalStateException("No authenticated user"))
        val validationError = validateFile(fileUri, contentResolver)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        val fileName = resolveFileName(fileUri, contentResolver)
        return documentRepository.uploadDocument(
            uid = currentUid,
            fileUri = fileUri,
            fileName = fileName,
            userTag = userTag,
            consent = consent,
            documentCategory = documentCategory,
            chatEligible = chatEligible,
            contentResolver = contentResolver,
            onProgress = onProgress
        ).map {
            SecureDocumentIntakeResult(fileName = fileName)
        }
    }

    private fun queryFileSize(uri: Uri, contentResolver: ContentResolver): Long? {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) {
                val size = cursor.getLong(index)
                return if (size >= 0) size else null
            }
        }
        return null
    }
}
