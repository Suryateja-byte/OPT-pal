package com.sidekick.opt_pal.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import com.sidekick.opt_pal.core.security.DocumentCryptoService
import com.sidekick.opt_pal.core.security.SecureDocumentContentClient
import com.sidekick.opt_pal.data.model.DocumentCategory
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.model.SecureDocumentContent
import com.sidekick.opt_pal.data.model.SecureUploadPreparation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class DocumentRepositoryImpl(
    private val documentCryptoService: DocumentCryptoService,
    private val secureDocumentContentClient: SecureDocumentContentClient
) : DocumentRepository {

    private val firestore = Firebase.firestore
    private val storage = Firebase.storage
    private val functions = Firebase.functions
    private val usersCollection = firestore.collection("users")

    private fun documentCollection(uid: String) =
        usersCollection.document(uid).collection("documents")

    override fun getDocuments(uid: String): Flow<List<DocumentMetadata>> {
        return documentCollection(uid)
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot -> snapshot.toObjects<DocumentMetadata>() }
    }

    override suspend fun uploadDocument(
        uid: String,
        fileUri: Uri,
        fileName: String,
        userTag: String,
        consent: DocumentUploadConsent,
        documentCategory: DocumentCategory,
        chatEligible: Boolean?,
        contentResolver: ContentResolver,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val plainBytes = contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: error("Unable to open file for upload.")
            val contentType = contentResolver.getType(fileUri).orEmpty().ifBlank {
                guessContentType(fileName)
            }
            val encryptedPayload = documentCryptoService.encryptDocument(plainBytes)
            val preparation = prepareSecureUpload(
                fileName = fileName,
                userTag = userTag,
                contentType = contentType,
                byteSize = plainBytes.size.toLong(),
                consent = consent,
                documentCategory = documentCategory,
                chatEligible = chatEligible ?: (
                    documentCategory != DocumentCategory.TAX_SENSITIVE &&
                        consent.processingMode == com.sidekick.opt_pal.data.model.DocumentProcessingMode.ANALYZE
                    ),
                encryptedDocumentKey = Base64.encodeToString(encryptedPayload.keyBytes, Base64.NO_WRAP)
            )

            val metadata = StorageMetadata.Builder()
                .setContentType("application/octet-stream")
                .setCustomMetadata("documentId", preparation.documentId)
                .setCustomMetadata("encryptionVersion", preparation.encryptionVersion.toString())
                .setCustomMetadata("originalContentType", contentType)
                .build()
            val uploadTask = storage.reference
                .child(preparation.storagePath)
                .putBytes(encryptedPayload.encryptedBytes, metadata)

            uploadTask.addOnProgressListener { snapshot ->
                onProgress(snapshot.bytesTransferred, snapshot.totalByteCount)
            }
            uploadTask.await()
            onProgress(1L, 1L)
        }
    }

    override suspend fun deleteDocument(uid: String, document: DocumentMetadata): Result<Unit> {
        return try {
            storage.reference.child(document.storagePath).delete().await()
            firestore.collection("users").document(uid)
                .collection("documents").document(document.id)
                .delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameDocument(uid: String, document: DocumentMetadata, newName: String): Result<Unit> {
        return try {
            firestore.collection("users").document(uid)
                .collection("documents").document(document.id)
                .update("userTag", newName)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDocumentContent(document: DocumentMetadata): Result<SecureDocumentContent> {
        return secureDocumentContentClient.downloadDocument(document.id).mapCatching { bytes ->
            SecureDocumentContent(
                fileName = document.fileName,
                contentType = document.contentType.ifBlank { guessContentType(document.fileName) },
                bytes = bytes
            )
        }
    }

    override suspend fun reprocessDocuments(): Result<String> {
        return try {
            val result = functions
                .getHttpsCallable("reprocessDocuments")
                .call()
                .await()

            val data = result.data as? Map<String, Any>
            val message = data?.get("message") as? String ?: "Reprocessing started"
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun prepareSecureUpload(
        fileName: String,
        userTag: String,
        contentType: String,
        byteSize: Long,
        consent: DocumentUploadConsent,
        documentCategory: DocumentCategory,
        chatEligible: Boolean,
        encryptedDocumentKey: String
    ): SecureUploadPreparation {
        val response = functions
            .getHttpsCallable("prepareSecureUpload")
            .call(
                mapOf(
                    "fileName" to fileName,
                    "userTag" to userTag,
                    "contentType" to contentType,
                    "byteSize" to byteSize,
                    "processingMode" to consent.processingMode.wireValue,
                    "processingConsentAcceptedAt" to consent.acceptedAt,
                    "consentProviders" to consent.providers,
                    "documentCategory" to documentCategory.wireValue,
                    "chatEligible" to chatEligible,
                    "encryptedDocumentKey" to encryptedDocumentKey
                )
            )
            .await()

        val data = response.data as? Map<*, *> ?: error("Invalid secure upload response.")
        val documentId = data["documentId"] as? String ?: error("Missing documentId.")
        val storagePath = data["storagePath"] as? String ?: error("Missing storagePath.")
        val encryptionVersion = (data["encryptionVersion"] as? Number)?.toInt() ?: 1
        return SecureUploadPreparation(
            documentId = documentId,
            storagePath = storagePath,
            encryptionVersion = encryptionVersion
        )
    }

    private fun guessContentType(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".heic", ignoreCase = true) -> "image/heic"
            fileName.endsWith(".heif", ignoreCase = true) -> "image/heif"
            else -> "image/jpeg"
        }
    }
}
