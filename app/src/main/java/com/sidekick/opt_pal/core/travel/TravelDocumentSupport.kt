package com.sidekick.opt_pal.core.travel

import com.sidekick.opt_pal.core.calculations.calculateUnemploymentForecast
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.TravelEvidenceSnapshot
import com.sidekick.opt_pal.data.model.UserProfile
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private enum class TravelDocumentType {
    PASSPORT,
    VISA,
    I20,
    EAD
}

private data class TravelDocumentCandidate(
    val documentId: String,
    val sourceLabel: String,
    val type: TravelDocumentType,
    val extractedData: Map<String, Any>,
    val processedAt: Long
)

fun buildTravelEvidenceSnapshot(
    documents: List<DocumentMetadata>,
    profile: UserProfile?,
    employments: List<Employment>,
    now: Long
): TravelEvidenceSnapshot {
    val candidates = documents.mapNotNull(::toTravelDocumentCandidate)
    val passport = candidates.latestOfType(TravelDocumentType.PASSPORT)
    val visa = candidates.latestOfType(TravelDocumentType.VISA)
    val i20 = candidates.latestOfType(TravelDocumentType.I20)
    val ead = candidates.latestOfType(TravelDocumentType.EAD)
    val forecast = calculateUnemploymentForecast(
        optType = profile?.optType,
        optStartDate = profile?.optStartDate,
        unemploymentTrackingStartDate = profile?.unemploymentTrackingStartDate,
        optEndDate = profile?.optEndDate,
        employments = employments,
        now = now
    )

    return TravelEvidenceSnapshot(
        sourceDocumentIds = listOfNotNull(passport?.documentId, visa?.documentId, i20?.documentId, ead?.documentId),
        passportSourceLabel = passport?.sourceLabel,
        passportIssuingCountry = passport?.readString(
            "passport_issuing_country",
            "issuing_country",
            "country_of_issuance",
            "nationality"
        ),
        passportExpirationDate = passport?.readDateMillis(
            "passport_expiration_date",
            "expiration_date",
            "expiry_date",
            "date_of_expiration",
            "expires_on"
        ),
        visaSourceLabel = visa?.sourceLabel,
        visaClass = visa?.readString("visa_class", "visa_type", "class", "type"),
        visaExpirationDate = visa?.readDateMillis(
            "visa_expiration_date",
            "expiration_date",
            "expiry_date",
            "expires_on",
            "visa_expires"
        ),
        i20SourceLabel = i20?.sourceLabel,
        i20TravelSignatureDate = i20?.readDateMillis(
            "travel_signature_date",
            "travel_endorsement_date",
            "travel_signature",
            "travel_authorization_date"
        ),
        eadSourceLabel = ead?.sourceLabel,
        eadExpirationDate = ead?.readDateMillis(
            "opt_end_date",
            "ead_end_date",
            "end_date",
            "valid_to",
            "employment_end_date"
        ),
        optType = profile?.optType
            ?: i20?.readString("opt_type")
            ?: ead?.readString("opt_type"),
        optEndDate = profile?.optEndDate
            ?: ead?.readDateMillis("opt_end_date", "ead_end_date", "valid_to"),
        hasCurrentEmploymentRecord = employments.any { employment ->
            employment.startDate <= now && (employment.endDate == null || employment.endDate >= now)
        },
        unemploymentDaysUsed = forecast.usedDays,
        unemploymentDaysAllowed = forecast.allowedDays
    )
}

private fun toTravelDocumentCandidate(document: DocumentMetadata): TravelDocumentCandidate? {
    if (DocumentProcessingMode.fromWireValue(document.processingMode) != DocumentProcessingMode.ANALYZE) {
        return null
    }
    if (document.processingStatus != "processed") {
        return null
    }
    val extractedData = document.extractedData.orEmpty()
    val type = normalizeTravelDocumentType(
        rawValue = buildString {
            append(document.documentType)
            append(' ')
            append(document.userTag)
            append(' ')
            append(document.fileName)
        },
        extractedData = extractedData
    ) ?: return null
    return TravelDocumentCandidate(
        documentId = document.id,
        sourceLabel = document.userTag.ifBlank { document.fileName },
        type = type,
        extractedData = extractedData,
        processedAt = document.processedAt ?: document.uploadedAt
    )
}

private fun normalizeTravelDocumentType(
    rawValue: String,
    extractedData: Map<String, Any>
): TravelDocumentType? {
    val normalized = normalizeLabel(rawValue)
    val keys = extractedData.keys.map(::normalizeLabel)
    return when {
        normalized.contains("passport") || keys.contains("passportissuingcountry") -> TravelDocumentType.PASSPORT
        normalized.contains("visa") || keys.contains("visaclass") -> TravelDocumentType.VISA
        normalized.contains("i20") || keys.contains("travelsignaturedate") -> TravelDocumentType.I20
        normalized.contains("ead") || normalized.contains("employmentauthorization") || keys.contains("eadcategory") -> TravelDocumentType.EAD
        else -> null
    }
}

private fun List<TravelDocumentCandidate>.latestOfType(type: TravelDocumentType): TravelDocumentCandidate? {
    return filter { it.type == type }.maxByOrNull { it.processedAt }
}

private fun TravelDocumentCandidate.readString(vararg aliases: String): String? {
    val values = extractedData.normalizedKeys()
    return aliases.firstNotNullOfOrNull { alias ->
        values[normalizeLabel(alias)]?.toString()?.trim()?.takeIf(String::isNotBlank)
    }
}

private fun TravelDocumentCandidate.readDateMillis(vararg aliases: String): Long? {
    val values = extractedData.normalizedKeys()
    return aliases.firstNotNullOfOrNull { alias ->
        values[normalizeLabel(alias)]?.toString()?.trim()?.let(::parseDateToMillis)
    }
}

private fun Map<String, Any>.normalizedKeys(): Map<String, Any> {
    return mapKeys { normalizeLabel(it.key) }
}

private fun parseDateToMillis(value: String): Long? {
    val normalized = value.trim()
    val direct = normalized.toLongOrNull()
    if (direct != null) {
        return if (direct > 9_999_999_999L) direct else direct * 1000L
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

private fun normalizeLabel(value: String): String {
    return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}
