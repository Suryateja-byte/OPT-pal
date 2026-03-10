package com.sidekick.opt_pal.data.model

enum class DocumentProcessingMode(val wireValue: String) {
    STORAGE_ONLY("storage_only"),
    ANALYZE("analyze");

    companion object {
        fun fromWireValue(value: String?): DocumentProcessingMode {
            return entries.firstOrNull { it.wireValue == value } ?: ANALYZE
        }
    }
}

object DocumentProcessingProviders {
    val analyzeProviders: List<String> = listOf(
        "Google Cloud Vision",
        "Vertex AI Gemini"
    )
}

data class DocumentUploadConsent(
    val processingMode: DocumentProcessingMode,
    val acceptedAt: Long = System.currentTimeMillis(),
    val providers: List<String> = when (processingMode) {
        DocumentProcessingMode.STORAGE_ONLY -> emptyList()
        DocumentProcessingMode.ANALYZE -> DocumentProcessingProviders.analyzeProviders
    }
)

data class SecureUploadPreparation(
    val documentId: String,
    val storagePath: String,
    val encryptionVersion: Int
)

data class SecureDocumentContent(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)

data class ChatDocumentRef(
    val documentId: String = "",
    val fileName: String = "",
    val label: String = ""
)
