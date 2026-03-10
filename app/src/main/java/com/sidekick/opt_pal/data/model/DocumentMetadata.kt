package com.sidekick.opt_pal.data.model

import com.google.firebase.firestore.DocumentId

/** Metadata stored in Firestore for each uploaded document. */
data class DocumentMetadata(
    @DocumentId val id: String = "",
    val fileName: String = "",
    val userTag: String = "",
    val storagePath: String = "",
    val downloadUrl: String = "",
    val contentType: String = "",
    val byteSize: Long = 0L,
    val encryptionVersion: Int = 0,
    val processingMode: String = DocumentProcessingMode.ANALYZE.wireValue,
    val processingConsentAcceptedAt: Long? = null,
    val processingStatus: String = "",
    val processingError: String = "",
    val documentType: String = "",
    val summary: String = "",
    val extractedData: Map<String, Any>? = null,
    val processedAt: Long? = null,
    val uploadedAt: Long = System.currentTimeMillis()
)
