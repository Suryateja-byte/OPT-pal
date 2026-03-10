package com.sidekick.opt_pal.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.sidekick.opt_pal.core.security.SecurityManager
import com.sidekick.opt_pal.data.local.DocumentDao
import com.sidekick.opt_pal.data.model.*
import com.sidekick.opt_pal.data.ml.MLKitOCRService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Secure Document Repository orchestrating the AI pipeline:
 * Scan -> Encrypt -> Upload -> OCR -> Extract -> Store
 */
class SecureDocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val securityManager: SecurityManager,
    private val ocrService: MLKitOCRService,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) {
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "SecureDocumentRepo"
    }
    
    /**
     * Process a newly scanned document through the complete pipeline
     */
    suspend fun processNewDocument(
        localImageUri: Uri,
        userProvidedName: String? = null
    ): Result<SecureDocument> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUser?.uid 
                ?: return@withContext Result.failure(Exception("User not authenticated"))
            
            val documentId = UUID.randomUUID().toString()
            
            // Step 1: OCR - Extract text locally
            Log.d(TAG, "Step 1: Running OCR...")
            val ocrResult = ocrService.extractTextFromImage(localImageUri)
            val ocrText = ocrResult.getOrNull() 
                ?: return@withContext Result.failure(Exception("OCR failed"))
            
            // Step 2: Classify document (Skipped - AI removed)
            Log.d(TAG, "Step 2: Classifying document... (Skipped)")
            val documentType = DocumentType.OTHER
            
            // Step 3: Extract structured data (Skipped - AI removed)
            Log.d(TAG, "Step 3: Extracting data... (Skipped)")
            val extractedData = ExtractedDocumentData()
            
            // Step 4: Encrypt extracted data
            Log.d(TAG, "Step 4: Encrypting extracted data...")
            val extractedDataJson = gson.toJson(extractedData)
            val encryptedData = securityManager.encryptString(extractedDataJson)
            val encryptedDataBase64 = android.util.Base64.encodeToString(
                encryptedData, 
                android.util.Base64.DEFAULT
            )
            
            val encryptedOcrText = securityManager.encryptString(ocrText)
            val encryptedOcrBase64 = android.util.Base64.encodeToString(
                encryptedOcrText,
                android.util.Base64.DEFAULT
            )
            
            // Step 5: Encrypt and upload the actual document file
            Log.d(TAG, "Step 5: Uploading encrypted document...")
            val storageUri = uploadEncryptedDocument(userId, documentId, localImageUri)
            
            // Step 6: Generate suggested file name
            val suggestedName = userProvidedName 
                ?: "Document_${System.currentTimeMillis()}.pdf"
            
            // Step 7: Create document metadata
            val document = SecureDocument(
                id = documentId,
                userId = userId,
                documentType = documentType,
                displayName = suggestedName,
                storageUri = storageUri,
                uploadedAt = System.currentTimeMillis(),
                lastModified = System.currentTimeMillis(),
                encryptedExtractedData = encryptedDataBase64,
                encryptedOcrText = encryptedOcrBase64,
                extractionStatus = ExtractionStatus.COMPLETED
            )
            
            // Step 8: Save to local database
            documentDao.insertDocument(document)
            
            // Step 9: Sync to Firestore (encrypted metadata)
            syncDocumentToFirestore(document)
            
            Log.d(TAG, "Document processed successfully: $documentId")
            Result.success(document)
            
        } catch (e: Exception) {
            Log.e(TAG, "Document processing failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload encrypted document to Firebase Storage
     */
    private suspend fun uploadEncryptedDocument(
        userId: String,
        documentId: String,
        localUri: Uri
    ): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(localUri)
            ?: throw Exception("Cannot read file")
        
        val bytes = inputStream.readBytes()
        inputStream.close()
        
        val encryptedBytes = securityManager.encryptData(bytes)
        
        val storagePath = "users/$userId/documents/$documentId.enc"
        val storageRef = storage.reference.child(storagePath)
        
        storageRef.putBytes(encryptedBytes).await()
        
        storagePath
    }
    
    private suspend fun syncDocumentToFirestore(document: SecureDocument) = withContext(Dispatchers.IO) {
        try {
            val docRef = firestore
                .collection("users")
                .document(document.userId)
                .collection("documents")
                .document(document.id)
            
            docRef.set(document).await()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore sync failed", e)
        }
    }
    
    fun getAllDocuments(): Flow<List<SecureDocument>> {
        val userId = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return documentDao.getAllDocuments(userId)
    }
    
    suspend fun getDocumentWithDecryptedData(documentId: String): Result<Pair<SecureDocument, ExtractedDocumentData?>> {
        return try {
            val document = documentDao.getDocumentById(documentId)
                ?: return Result.failure(Exception("Document not found"))
            
            val extractedData = document.encryptedExtractedData?.let { encryptedBase64 ->
                try {
                    val encryptedBytes = android.util.Base64.decode(encryptedBase64, android.util.Base64.DEFAULT)
                    val decryptedJson = securityManager.decryptString(encryptedBytes)
                    gson.fromJson(decryptedJson, ExtractedDocumentData::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decrypt extracted data", e)
                    null
                }
            }
            
            Result.success(Pair(document, extractedData))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteDocument(documentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val document = documentDao.getDocumentById(documentId)
                ?: return@withContext Result.failure(Exception("Document not found"))
            
            documentDao.deleteDocument(document)
            
            try {
                storage.reference.child(document.storageUri).delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete from storage", e)
            }
            
            try {
                firestore
                    .collection("users")
                    .document(document.userId)
                    .collection("documents")
                    .document(document.id)
                    .delete()
                    .await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete from Firestore", e)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
