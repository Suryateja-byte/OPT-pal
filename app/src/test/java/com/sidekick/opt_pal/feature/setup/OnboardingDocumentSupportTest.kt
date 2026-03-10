package com.sidekick.opt_pal.feature.setup

import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.OnboardingDocumentType
import com.sidekick.opt_pal.data.model.OnboardingField
import com.sidekick.opt_pal.data.model.OptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class OnboardingDocumentSupportTest {

    @Test
    fun i20CandidateReadsCanonicalFields() {
        val candidate = document(
            id = "i20-1",
            documentType = "I-20 Form",
            extractedData = mapOf(
                "sevis_id" to "N1234567890",
                "school_name" to "State University",
                "cip_code" to "11.0701",
                "major_name" to "Computer Science",
                "opt_type" to "STEM",
                "opt_start_date" to "2025-06-01"
            )
        ).toOnboardingCandidate()

        requireNotNull(candidate)
        assertEquals(OnboardingDocumentType.I20, candidate.documentType)
        assertEquals("N1234567890", candidate.fields.sevisId)
        assertEquals("State University", candidate.fields.schoolName)
        assertEquals("11.0701", candidate.fields.cipCode)
        assertEquals("Computer Science", candidate.fields.majorName)
        assertEquals(OptType.STEM, candidate.fields.optType)
        assertEquals(date(2025, 6, 1), candidate.fields.optStartDate)
    }

    @Test
    fun eadCandidateUnderstandsLegacyAliases() {
        val candidate = document(
            id = "ead-1",
            documentType = "EAD Card",
            extractedData = mapOf(
                "category" to "C03C",
                "ead_start_date" to "06/01/2025",
                "ead_end_date" to "05/31/2027",
                "a_number" to "123456789"
            )
        ).toOnboardingCandidate()

        requireNotNull(candidate)
        assertEquals(OnboardingDocumentType.EAD, candidate.documentType)
        assertEquals(OptType.STEM, candidate.fields.optType)
        assertEquals(date(2025, 6, 1), candidate.fields.optStartDate)
        assertEquals(date(2027, 5, 31), candidate.fields.optEndDate)
        assertEquals("123456789", candidate.fields.uscisNumber)
    }

    @Test
    fun buildDraftPrefersI20IdentityAndEadDates() {
        val i20 = requireNotNull(
            document(
                id = "i20-2",
                fileName = "i20.pdf",
                userTag = "I-20",
                documentType = "I-20 Form",
                extractedData = mapOf(
                    "sevis_id" to "N0000000001",
                    "school_name" to "Tech University",
                    "cip_code" to "14.0901",
                    "major_name" to "Electrical Engineering",
                    "opt_type" to "Initial",
                    "opt_start_date" to "2025-02-01"
                )
            ).toOnboardingCandidate()
        )
        val ead = requireNotNull(
            document(
                id = "ead-2",
                fileName = "ead.pdf",
                userTag = "EAD",
                documentType = "EAD Card",
                extractedData = mapOf(
                    "category" to "C03C",
                    "opt_start_date" to "2025-03-01",
                    "opt_end_date" to "2027-02-28"
                )
            ).toOnboardingCandidate()
        )

        val draft = buildOnboardingDraft(listOf(i20, ead))

        assertEquals(OptType.INITIAL, draft.optType)
        assertEquals(date(2025, 3, 1), draft.optStartDate)
        assertEquals(date(2027, 2, 28), draft.optEndDate)
        assertEquals("N0000000001", draft.sevisId)
        assertEquals("Tech University", draft.schoolName)
        assertEquals("14.0901", draft.cipCode)
        assertEquals("Electrical Engineering", draft.majorName)
        assertEquals(listOf("i20-2", "ead-2"), draft.sourceDocumentIds)
        assertEquals("I-20", draft.fieldSources[OnboardingField.SEVIS_ID])
        assertEquals("EAD", draft.fieldSources[OnboardingField.OPT_START_DATE])
        assertEquals("I-20", draft.fieldSources[OnboardingField.MAJOR_NAME])
    }

    @Test
    fun ignoresNonAnalyzedOrErroredDocuments() {
        val storageOnly = document(
            id = "doc-1",
            processingMode = DocumentProcessingMode.STORAGE_ONLY.wireValue,
            documentType = "I-20 Form",
            extractedData = mapOf("sevis_id" to "N1234567890")
        )
        val errored = document(
            id = "doc-2",
            processingStatus = "error",
            documentType = "EAD Card",
            extractedData = mapOf("opt_start_date" to "2025-01-01")
        )

        assertNull(storageOnly.toOnboardingCandidate())
        assertNull(errored.toOnboardingCandidate())
    }

    private fun document(
        id: String,
        fileName: String = "document.pdf",
        userTag: String = "",
        processingMode: String = DocumentProcessingMode.ANALYZE.wireValue,
        processingStatus: String = "processed",
        documentType: String,
        extractedData: Map<String, Any>
    ): DocumentMetadata {
        return DocumentMetadata(
            id = id,
            fileName = fileName,
            userTag = userTag,
            processingMode = processingMode,
            processingStatus = processingStatus,
            documentType = documentType,
            extractedData = extractedData,
            processedAt = 10L
        )
    }

    private fun date(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }
}
