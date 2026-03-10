package com.sidekick.opt_pal.data.ml

/*
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeBackend
import com.google.firebase.ai.ai
import com.google.firebase.ai.generationConfig
import com.google.gson.Gson
import com.sidekick.opt_pal.data.model.DocumentType
import com.sidekick.opt_pal.data.model.ExtractedDocumentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI Service for document classification and information extraction
 * 
 * Uses Firebase AI SDK with GoogleAI backend for Gemini API access
 */
class GeminiAIService(private val apiKey: String) {
    
    private val gson = Gson()
    
    // Use Firebase AI SDK with GoogleAI backend
    private val model = Firebase.ai(backend = GenerativeBackend.googleAI(apiKey))
        .generativeModel(
            modelName = "gemini-1.5-flash",
            generationConfig = generationConfig {
                temperature = 0.1f  // Low temperature for consistent extraction
                topK = 1
                topP = 0.8f
            }
        )
    
    /**
     * Classify a document based on its OCR text
     * Returns the most likely document type
     */
    suspend fun classifyDocument(ocrText: String): Result<DocumentType> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Analyze the following text from an immigration document and classify it.
                
                Document text:
                $ocrText
                
                Classify this document as ONE of the following types:
                - I20: Form I-20 (Certificate of Eligibility for Nonimmigrant Student Status)
                - EAD_CARD: Employment Authorization Document (EAD Card)
                - PASSPORT: Passport
                - VISA_STAMP: Visa Stamp
                - OFFER_LETTER: Job Offer Letter
                - I797_APPROVAL: Form I-797 (USCIS Approval Notice)
                - I94: Form I-94 (Arrival/Departure Record)
                - OTHER: Other document type
                
                Respond with ONLY the classification (e.g., "I20" or "EAD_CARD"). No explanation needed.
            """.trimIndent()
            
            val response = model.generateContent(prompt)
            val classification = response.text?.trim()?.uppercase()?.replace("-", "_") ?: "OTHER"
            
            val documentType = try {
                DocumentType.valueOf(classification)
            } catch (e: IllegalArgumentException) {
                DocumentType.OTHER
            }
            
            Result.success(documentType)
        } catch (e: Exception) {
            Result.failure(Exception("Classification failed: ${e.message}"))
        }
    }
    
    /**
     * Extract structured data from document using Gemini's JSON mode
     * This is the core "Information Extraction" functionality
     */
    suspend fun extractDocumentData(
        ocrText: String,
        documentType: DocumentType
    ): Result<ExtractedDocumentData> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildExtractionPrompt(ocrText, documentType)
            val response = model.generateContent(prompt)
            val jsonResponse = response.text ?: "{}"
            
            // Parse JSON to ExtractedDocumentData
            val extractedData = try {
                gson.fromJson(jsonResponse, ExtractedDocumentData::class.java)
            } catch (e: Exception) {
                // If JSON parsing fails, return empty data
                ExtractedDocumentData()
            }
            
            Result.success(extractedData)
        } catch (e: Exception) {
            Result.failure(Exception("Extraction failed: ${e.message}"))
        }
    }
    
    /**
     * Build document-specific extraction prompt
     */
    private fun buildExtractionPrompt(ocrText: String, documentType: DocumentType): String {
        val fieldInstructions = when (documentType) {
            DocumentType.I20 -> """
                - sevisId: SEVIS ID (format: N followed by 10 digits)
                - programEndDate: Program end date
                - universityName: School name
                - degreeLevel: Degree level (Bachelor's, Master's, etc.)
                - majorField: Field of study
            """.trimIndent()
            
            DocumentType.EAD_CARD -> """
                - eadNumber: USCIS Number / Card Number
                - uscisANumber: A-Number (A followed by 8-9 digits)
                - eadStartDate: Valid from date
                - eadEndDate: Card expires date
            """.trimIndent()
            
            DocumentType.PASSPORT -> """
                - passportNumber: Passport number
                - passportExpiryDate: Date of expiry
            """.trimIndent()
            
            DocumentType.I94 -> """
                - i94Number: I-94 Admission Number
            """.trimIndent()
            
            else -> """
                - Extract any relevant fields you can identify
            """.trimIndent()
        }
        
        return """
            You are an expert at extracting structured information from immigration documents.
            
            Document Type: $documentType
            
            Document Text:
            $ocrText
            
            Extract the following information and return ONLY a valid JSON object with these fields:
            $fieldInstructions
            
            Rules:
            1. Return ONLY valid JSON, no markdown formatting, no explanation
            2. Use null for fields you cannot find
            3. Dates should be in format "YYYY-MM-DD" if possible, or the format found in the document
            4. Be precise - only extract information you are confident about
            
            JSON Response:
        """.trimIndent()
    }
    
    /**
     * Generate a suggested file name based on document type and extracted data
     */
    suspend fun suggestFileName(
        documentType: DocumentType,
        extractedData: ExtractedDocumentData
    ): String = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        
        when (documentType) {
            DocumentType.I20 -> {
                val sevis = extractedData.sevisId?.takeLast(4) ?: "XXXX"
                "I-20_$sevis.pdf"
            }
            DocumentType.EAD_CARD -> {
                val endDate = extractedData.eadEndDate?.replace("-", "") ?: "Active"
                "EAD_Card_$endDate.pdf"
            }
            DocumentType.PASSPORT -> {
                "Passport_${extractedData.passportNumber ?: timestamp}.pdf"
            }
            DocumentType.VISA_STAMP -> "Visa_Stamp_$timestamp.pdf"
            DocumentType.OFFER_LETTER -> {
                val employer = extractedData.employerName?.replace(" ", "_") ?: "Company"
                "Offer_${employer}.pdf"
            }
            DocumentType.I797_APPROVAL -> "I-797_Approval_$timestamp.pdf"
            DocumentType.I94 -> "I-94_Record_$timestamp.pdf"
            DocumentType.OTHER -> "Document_$timestamp.pdf"
        }
    }
}
*/
