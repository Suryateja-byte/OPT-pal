package com.sidekick.opt_pal.feature.tax

import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class W2DocumentSupportTest {

    @Test
    fun processedW2DocumentIsNormalizedIntoDraft() {
        val draft = DocumentMetadata(
            id = "doc-1",
            fileName = "w2.pdf",
            userTag = "2025 W-2",
            processingMode = DocumentProcessingMode.ANALYZE.wireValue,
            processingStatus = "processed",
            documentType = "W-2 Form",
            extractedData = mapOf(
                "employer" to "Acme Corp",
                "year" to "2025",
                "employee_ssn" to "***-**-6789",
                "employer_ein" to "12-3456789",
                "box_4" to "210.80",
                "box_6" to "49.30"
            )
        ).toW2ExtractionDraft()

        assertNotNull(draft)
        assertEquals("Acme Corp", draft?.employerName)
        assertEquals(2025, draft?.taxYear)
        assertEquals("6789", draft?.employeeSsnLast4)
        assertEquals("XX-XXX6789", draft?.employerEinMasked)
        assertEquals(210.8, draft?.socialSecurityTaxBox4)
        assertEquals(49.3, draft?.medicareTaxBox6)
    }

    @Test
    fun storageOnlyDocumentIsExcluded() {
        val draft = DocumentMetadata(
            id = "doc-2",
            fileName = "w2.pdf",
            processingMode = DocumentProcessingMode.STORAGE_ONLY.wireValue,
            processingStatus = "stored",
            documentType = "W-2 Form"
        ).toW2ExtractionDraft()

        assertNull(draft)
    }
}
