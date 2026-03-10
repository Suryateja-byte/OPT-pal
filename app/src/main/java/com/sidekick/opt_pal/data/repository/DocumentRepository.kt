package com.sidekick.opt_pal.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.sidekick.opt_pal.data.model.DocumentCategory
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.model.SecureDocumentContent
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getDocuments(uid: String): Flow<List<DocumentMetadata>>
    suspend fun uploadDocument(
        uid: String,
        fileUri: Uri,
        fileName: String,
        userTag: String,
        consent: DocumentUploadConsent,
        documentCategory: DocumentCategory = DocumentCategory.GENERAL,
        chatEligible: Boolean? = null,
        contentResolver: ContentResolver,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Unit>
    suspend fun deleteDocument(uid: String, document: DocumentMetadata): Result<Unit>
    suspend fun renameDocument(uid: String, document: DocumentMetadata, newName: String): Result<Unit>
    suspend fun getDocumentContent(document: DocumentMetadata): Result<SecureDocumentContent>
    suspend fun reprocessDocuments(): Result<String>
}
