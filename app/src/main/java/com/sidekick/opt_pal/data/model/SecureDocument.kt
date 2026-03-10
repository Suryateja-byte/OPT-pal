package com.sidekick.opt_pal.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Represents a document stored in the secure vault.
 * All sensitive fields are encrypted before storage.
 */
@Entity(tableName = "documents")
@TypeConverters(Converters::class)
data class SecureDocument(
    @PrimaryKey
    val id: String,
    val userId: String,
    val documentType: DocumentType,
    val displayName: String,
    val storageUri: String, // Firebase Storage path or local encrypted path
    val thumbnailUri: String? = null,
    val uploadedAt: Long,
    val lastModified: Long,
    
    // Encrypted extracted data (JSON string encrypted as byte array -> Base64 string for Room)
    // This contains SEVIS ID, EAD numbers, dates, etc.
    val encryptedExtractedData: String? = null,
    
    // OCR text (encrypted)
    val encryptedOcrText: String? = null,
    
    // File size in bytes
    val fileSizeBytes: Long = 0,
    
    // Extraction status
    val extractionStatus: ExtractionStatus = ExtractionStatus.PENDING
)

enum class DocumentType {
    I20,
    EAD_CARD,
    PASSPORT,
    VISA_STAMP,
    OFFER_LETTER,
    I797_APPROVAL,
    I94,
    OTHER
}

enum class ExtractionStatus {
    PENDING,      // Not yet processed
    PROCESSING,   // Currently being processed by AI
    COMPLETED,    // Successfully extracted
    FAILED        // Extraction failed
}

/**
 * Extracted data from documents (stored encrypted)
 * This is the "Knowledge Graph" that the AI chatbot queries
 */
data class ExtractedDocumentData(
    val sevisId: String? = null,
    val eadNumber: String? = null,  // USCIS#
    val eadStartDate: String? = null,
    val eadEndDate: String? = null,
    val passportNumber: String? = null,
    val passportExpiryDate: String? = null,
    val visaNumber: String? = null,
    val visaExpiryDate: String? = null,
    val i94Number: String? = null,
    val employerName: String? = null,
    val uscisANumber: String? = null,
    val programEndDate: String? = null,
    val universityName: String? = null,
    val degreeLevel: String? = null,
    val majorField: String? = null,
    
    // Additional metadata
    val rawFields: Map<String, String> = emptyMap() // Catch-all for other fields
)

// Room Type Converters
class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromDocumentType(value: DocumentType): String = value.name
    
    @TypeConverter
    fun toDocumentType(value: String): DocumentType = DocumentType.valueOf(value)
    
    @TypeConverter
    fun fromExtractionStatus(value: ExtractionStatus): String = value.name
    
    @TypeConverter
    fun toExtractionStatus(value: String): ExtractionStatus = ExtractionStatus.valueOf(value)
}
