package com.sidekick.opt_pal.feature.setup

import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.NormalizedOnboardingFields
import com.sidekick.opt_pal.data.model.OnboardingDocumentCandidate
import com.sidekick.opt_pal.data.model.OnboardingDocumentType
import com.sidekick.opt_pal.data.model.OnboardingField
import com.sidekick.opt_pal.data.model.OnboardingProfileDraft
import com.sidekick.opt_pal.data.model.OnboardingSource
import com.sidekick.opt_pal.data.model.OptType
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

fun DocumentMetadata.toOnboardingCandidate(): OnboardingDocumentCandidate? {
    if (DocumentProcessingMode.fromWireValue(processingMode) != DocumentProcessingMode.ANALYZE) return null
    if (processingStatus != "processed") return null
    val normalizedType = normalizeOnboardingDocumentType(documentType.ifBlank { userTag })
        ?: normalizeOnboardingDocumentType(fileName)
        ?: return null
    val normalizedFields = normalizeOnboardingFields(normalizedType, extractedData.orEmpty())
    if (normalizedFields.isEmpty()) return null
    return OnboardingDocumentCandidate(
        documentId = id,
        fileName = fileName,
        displayName = userTag.ifBlank { fileName },
        documentType = normalizedType,
        processedAt = processedAt,
        fields = normalizedFields
    )
}

fun buildOnboardingDraft(candidates: List<OnboardingDocumentCandidate>): OnboardingProfileDraft {
    val i20 = candidates.firstOrNull { it.documentType == OnboardingDocumentType.I20 }
    val ead = candidates.firstOrNull { it.documentType == OnboardingDocumentType.EAD }
    val fieldSources = linkedMapOf<OnboardingField, String>()

    fun sourceLabel(candidate: OnboardingDocumentCandidate): String = candidate.displayName

    val optType = when {
        i20?.fields?.optType != null -> {
            fieldSources[OnboardingField.OPT_TYPE] = sourceLabel(i20)
            i20.fields.optType
        }
        ead?.fields?.optType != null -> {
            fieldSources[OnboardingField.OPT_TYPE] = sourceLabel(ead)
            ead.fields.optType
        }
        else -> null
    }

    val optStartDate = when {
        ead?.fields?.optStartDate != null -> {
            fieldSources[OnboardingField.OPT_START_DATE] = sourceLabel(ead)
            ead.fields.optStartDate
        }
        i20?.fields?.optStartDate != null -> {
            fieldSources[OnboardingField.OPT_START_DATE] = sourceLabel(i20)
            i20.fields.optStartDate
        }
        else -> null
    }

    val optEndDate = when {
        ead?.fields?.optEndDate != null -> {
            fieldSources[OnboardingField.OPT_END_DATE] = sourceLabel(ead)
            ead.fields.optEndDate
        }
        i20?.fields?.optEndDate != null -> {
            fieldSources[OnboardingField.OPT_END_DATE] = sourceLabel(i20)
            i20.fields.optEndDate
        }
        else -> null
    }

    val sevisId = i20?.fields?.sevisId.orEmpty().ifBlank { ead?.fields?.sevisId.orEmpty() }
    if (sevisId.isNotBlank()) {
        fieldSources[OnboardingField.SEVIS_ID] = sourceLabel(i20 ?: ead!!)
    }

    val schoolName = i20?.fields?.schoolName.orEmpty().ifBlank { ead?.fields?.schoolName.orEmpty() }
    if (schoolName.isNotBlank()) {
        fieldSources[OnboardingField.SCHOOL_NAME] = sourceLabel(i20 ?: ead!!)
    }

    val cipCode = i20?.fields?.cipCode.orEmpty().ifBlank { ead?.fields?.cipCode.orEmpty() }
    if (cipCode.isNotBlank()) {
        fieldSources[OnboardingField.CIP_CODE] = sourceLabel(i20 ?: ead!!)
    }

    return OnboardingProfileDraft(
        optType = optType,
        optStartDate = optStartDate,
        optEndDate = optEndDate,
        sevisId = sevisId,
        schoolName = schoolName,
        cipCode = cipCode,
        onboardingSource = if (candidates.isEmpty()) OnboardingSource.MANUAL else OnboardingSource.DOCUMENT_AI,
        sourceDocumentIds = candidates.map { it.documentId },
        fieldSources = fieldSources
    )
}

private fun normalizeOnboardingFields(
    documentType: OnboardingDocumentType,
    extractedData: Map<String, Any>
): NormalizedOnboardingFields {
    val values = extractedData.mapKeys { it.key.lowercase(Locale.US) }

    val sevisId = firstString(values, "sevis_id", "sevisid")
        ?.uppercase(Locale.US)
    val schoolName = firstString(values, "school_name", "schoolname", "university_name", "universityname")
    val cipCode = firstString(values, "cip_code", "cipcode", "major_code")
    val explicitOptType = firstString(values, "opt_type", "opttype")
        ?.toOptTypeOrNull()
    val eadCategory = firstString(values, "ead_category", "category")
    val inferredOptType = explicitOptType ?: eadCategory?.inferOptTypeFromCategory()
    val optStartDate = firstDateMillis(
        values,
        "opt_start_date",
        "optstartdate",
        "ead_start_date",
        "eadstartdate",
        "valid_from"
    )
    val optEndDate = firstDateMillis(
        values,
        "opt_end_date",
        "optenddate",
        "ead_end_date",
        "eadenddate",
        "valid_to"
    )
    val uscisNumber = firstString(
        values,
        "uscis_number",
        "uscisanumber",
        "a_number",
        "ead_number"
    )

    return when (documentType) {
        OnboardingDocumentType.I20 -> NormalizedOnboardingFields(
            sevisId = sevisId,
            schoolName = schoolName,
            cipCode = cipCode,
            optType = inferredOptType,
            optStartDate = optStartDate,
            optEndDate = optEndDate
        )
        OnboardingDocumentType.EAD -> NormalizedOnboardingFields(
            optType = inferredOptType,
            optStartDate = optStartDate,
            optEndDate = optEndDate,
            eadCategory = eadCategory,
            uscisNumber = uscisNumber
        )
    }
}

private fun normalizeOnboardingDocumentType(rawValue: String?): OnboardingDocumentType? {
    val value = rawValue?.lowercase(Locale.US)?.replace(" ", "").orEmpty()
    return when {
        value.contains("i-20") || value.contains("i20") -> OnboardingDocumentType.I20
        value.contains("ead") || value.contains("employmentauthorization") -> OnboardingDocumentType.EAD
        else -> null
    }
}

private fun NormalizedOnboardingFields.isEmpty(): Boolean {
    return sevisId == null &&
        schoolName == null &&
        cipCode == null &&
        optType == null &&
        optStartDate == null &&
        optEndDate == null &&
        eadCategory == null &&
        uscisNumber == null
}

private fun firstString(values: Map<String, Any>, vararg aliases: String): String? {
    return aliases.firstNotNullOfOrNull { alias ->
        val raw = values[alias] ?: return@firstNotNullOfOrNull null
        raw.toString().trim().takeIf { it.isNotBlank() }
    }
}

private fun firstDateMillis(values: Map<String, Any>, vararg aliases: String): Long? {
    return aliases.firstNotNullOfOrNull { alias ->
        val raw = values[alias]?.toString()?.trim() ?: return@firstNotNullOfOrNull null
        parseDateToMillis(raw)
    }
}

private fun parseDateToMillis(value: String): Long? {
    val normalized = value.trim()
    val direct = runCatching { normalized.toLong() }.getOrNull()
    if (direct != null) {
        return direct
    }
    val patterns = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    )
    for (formatter in patterns) {
        try {
            return LocalDate.parse(normalized, formatter)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
            // Try the next format.
        }
    }
    return null
}

private fun String.toOptTypeOrNull(): OptType? {
    val normalized = lowercase(Locale.US)
    return when {
        normalized.contains("stem") -> OptType.STEM
        normalized.contains("initial") || normalized.contains("post-completion") -> OptType.INITIAL
        else -> null
    }
}

private fun String.inferOptTypeFromCategory(): OptType? {
    val normalized = lowercase(Locale.US).replace(" ", "")
    return when {
        normalized.contains("stem") || normalized.contains("c03c") || normalized.contains("c3c") -> OptType.STEM
        normalized.contains("c03b") || normalized.contains("c3b") ||
            normalized.contains("c03a") || normalized.contains("c3a") -> OptType.INITIAL
        else -> null
    }
}
