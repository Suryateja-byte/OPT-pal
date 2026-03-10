package com.sidekick.opt_pal.core.travel

import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TravelDocumentSupportTest {

    @Test
    fun buildsEvidenceFromPassportVisaAndI20() {
        val evidence = buildTravelEvidenceSnapshot(
            documents = listOf(
                doc(
                    id = "passport-1",
                    documentType = "Passport Bio Page",
                    userTag = "Passport",
                    extractedData = mapOf(
                        "passport_issuing_country" to "India",
                        "passport_expiration_date" to "2027-01-01"
                    )
                ),
                doc(
                    id = "visa-1",
                    documentType = "F-1 Visa",
                    userTag = "F-1 Visa",
                    extractedData = mapOf(
                        "visa_class" to "F-1",
                        "visa_expiration_date" to "2026-12-31"
                    )
                ),
                doc(
                    id = "i20-1",
                    documentType = "I-20 Form",
                    userTag = "Latest I-20",
                    extractedData = mapOf(
                        "travel_signature_date" to "2026-02-10"
                    )
                ),
                doc(
                    id = "ead-1",
                    documentType = "EAD Card",
                    userTag = "EAD",
                    extractedData = mapOf(
                        "opt_end_date" to "2026-12-31"
                    )
                )
            ),
            profile = UserProfile(
                uid = "user-1",
                optType = "initial",
                optEndDate = date(2026, 12, 31)
            ),
            employments = listOf(
                Employment(
                    id = "employment-1",
                    employerName = "Acme",
                    jobTitle = "Engineer",
                    startDate = date(2026, 1, 1),
                    endDate = null,
                    hoursPerWeek = 40
                )
            ),
            now = date(2026, 3, 10)
        )

        assertEquals("India", evidence.passportIssuingCountry)
        assertEquals(date(2027, 1, 1), evidence.passportExpirationDate)
        assertEquals("F-1", evidence.visaClass)
        assertEquals(date(2026, 12, 31), evidence.visaExpirationDate)
        assertEquals(date(2026, 2, 10), evidence.i20TravelSignatureDate)
        assertEquals(date(2026, 12, 31), evidence.eadExpirationDate)
        assertTrue(evidence.hasCurrentEmploymentRecord)
    }

    @Test
    fun latestProcessedTravelDocumentWins() {
        val evidence = buildTravelEvidenceSnapshot(
            documents = listOf(
                doc(
                    id = "passport-old",
                    processedAt = 5L,
                    documentType = "Passport Bio Page",
                    userTag = "Old Passport",
                    extractedData = mapOf(
                        "passport_issuing_country" to "India",
                        "passport_expiration_date" to "2026-06-01"
                    )
                ),
                doc(
                    id = "passport-new",
                    processedAt = 10L,
                    documentType = "Passport Bio Page",
                    userTag = "New Passport",
                    extractedData = mapOf(
                        "passport_issuing_country" to "India",
                        "passport_expiration_date" to "2027-06-01"
                    )
                )
            ),
            profile = UserProfile(uid = "user-1"),
            employments = emptyList(),
            now = date(2026, 3, 10)
        )

        assertEquals("New Passport", evidence.passportSourceLabel)
        assertEquals(date(2027, 6, 1), evidence.passportExpirationDate)
    }

    @Test
    fun ignoredDocumentsDoNotPolluteEvidence() {
        val evidence = buildTravelEvidenceSnapshot(
            documents = listOf(
                doc(
                    id = "storage-only",
                    processingMode = DocumentProcessingMode.STORAGE_ONLY.wireValue,
                    documentType = "Passport Bio Page",
                    extractedData = mapOf(
                        "passport_issuing_country" to "India"
                    )
                ),
                doc(
                    id = "errored",
                    processingStatus = "error",
                    documentType = "F-1 Visa",
                    extractedData = mapOf(
                        "visa_class" to "F-1"
                    )
                )
            ),
            profile = UserProfile(uid = "user-1"),
            employments = emptyList(),
            now = date(2026, 3, 10)
        )

        assertEquals(null, evidence.passportIssuingCountry)
        assertEquals(null, evidence.visaClass)
    }

    private fun doc(
        id: String,
        processedAt: Long = 10L,
        processingMode: String = DocumentProcessingMode.ANALYZE.wireValue,
        processingStatus: String = "processed",
        documentType: String,
        userTag: String = "",
        extractedData: Map<String, Any>
    ): DocumentMetadata {
        return DocumentMetadata(
            id = id,
            fileName = "$id.pdf",
            userTag = userTag,
            processingMode = processingMode,
            processingStatus = processingStatus,
            documentType = documentType,
            extractedData = extractedData,
            processedAt = processedAt
        )
    }

    private fun date(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }
}
