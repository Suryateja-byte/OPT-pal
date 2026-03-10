package com.sidekick.opt_pal.data.model

import kotlin.math.max

enum class TravelOutcome {
    GO,
    CAUTION,
    NO_GO,
    NO_GO_CONTACT_DSO_OR_ATTORNEY
}

enum class TravelChecklistStatus {
    PASS,
    CAUTION,
    BLOCK,
    ESCALATE
}

enum class TravelRuleId {
    ESCALATION,
    GRACE_PERIOD,
    PASSPORT_VALIDITY,
    VISA_PATH,
    I20_SIGNATURE,
    EAD_STATUS,
    EMPLOYMENT_EVIDENCE,
    UNEMPLOYMENT_LIMIT,
    COUNTRY_RESTRICTIONS
}

enum class TravelScenario {
    PENDING_INITIAL_OPT,
    APPROVED_POST_COMPLETION_OPT,
    PENDING_STEM_EXTENSION,
    APPROVED_STEM_OPT,
    GRACE_PERIOD
}

enum class TravelEntitlementSource {
    USER_FLAG,
    OPEN_BETA,
    LOCKED_PREVIEW
}

data class TravelEntitlementState(
    val isEnabled: Boolean = false,
    val source: TravelEntitlementSource = TravelEntitlementSource.LOCKED_PREVIEW,
    val message: String = "Travel Risk Advisor is not enabled for this account yet."
)

data class TravelTripInput(
    val departureDate: Long? = null,
    val plannedReturnDate: Long? = null,
    val destinationCountry: String = "",
    val onlyCanadaMexicoAdjacentIslands: Boolean? = null,
    val needsNewVisa: Boolean? = null,
    val visaRenewalOutsideResidence: Boolean? = null,
    val hasEmploymentOrOfferProof: Boolean? = null,
    val capGapActive: Boolean? = null,
    val hasRfeStatusIssueOrArrestHistory: Boolean? = null,
    val travelScenario: TravelScenario? = null,
    val passportIssuingCountry: String = "",
    val passportExpirationDate: Long? = null,
    val visaClass: String = "F-1",
    val visaExpirationDate: Long? = null,
    val i20TravelSignatureDate: Long? = null,
    val eadExpirationDate: Long? = null,
    val hasOriginalEadInHand: Boolean? = null
) {
    val daysAbroad: Int?
        get() {
            val departure = departureDate ?: return null
            val returnDate = plannedReturnDate ?: return null
            if (returnDate < departure) return null
            val millis = max(0L, returnDate - departure)
            return (millis / ONE_DAY_MILLIS).toInt() + 1
        }
}

data class TravelEvidenceSnapshot(
    val sourceDocumentIds: List<String> = emptyList(),
    val passportSourceLabel: String? = null,
    val passportIssuingCountry: String? = null,
    val passportExpirationDate: Long? = null,
    val visaSourceLabel: String? = null,
    val visaClass: String? = null,
    val visaExpirationDate: Long? = null,
    val i20SourceLabel: String? = null,
    val i20TravelSignatureDate: Long? = null,
    val eadSourceLabel: String? = null,
    val eadExpirationDate: Long? = null,
    val optType: String? = null,
    val optEndDate: Long? = null,
    val hasCurrentEmploymentRecord: Boolean = false,
    val unemploymentDaysUsed: Int = 0,
    val unemploymentDaysAllowed: Int = 90
)

data class TravelSourceCitation(
    val id: String = "",
    val label: String = "",
    val url: String = "",
    val effectiveDate: String? = null,
    val lastReviewedDate: String? = null,
    val summary: String = ""
)

data class TravelPolicyRuleCard(
    val ruleId: TravelRuleId = TravelRuleId.ESCALATION,
    val title: String = "",
    val summary: String = "",
    val citationIds: List<String> = emptyList()
)

data class TravelPassportValidityPolicy(
    val defaultAdditionalValidityMonths: Int = 6,
    val sixMonthClubCountries: List<String> = emptyList()
)

data class TravelAutomaticRevalidationPolicy(
    val allowedCountries: List<String> = listOf("Canada", "Mexico"),
    val adjacentIslands: List<String> = emptyList(),
    val excludedCountries: List<String> = emptyList(),
    val maxTripLengthDays: Int = 30,
    val summary: String = ""
)

data class TravelCountryRestriction(
    val nationality: String = "",
    val summary: String = "",
    val visaClasses: List<String> = listOf("F-1", "J-1", "M-1"),
    val appliesToNewVisaOnly: Boolean = true,
    val isFullSuspension: Boolean = false
)

data class TravelPolicyBundle(
    val version: String = "",
    val generatedAt: Long = 0L,
    val lastReviewedAt: Long = 0L,
    val staleAfterDays: Int = 30,
    val sources: List<TravelSourceCitation> = emptyList(),
    val ruleCards: List<TravelPolicyRuleCard> = emptyList(),
    val passportValidity: TravelPassportValidityPolicy = TravelPassportValidityPolicy(),
    val automaticRevalidation: TravelAutomaticRevalidationPolicy = TravelAutomaticRevalidationPolicy(),
    val countryRestrictions: List<TravelCountryRestriction> = emptyList()
) {
    fun isStale(now: Long): Boolean {
        if (lastReviewedAt <= 0L) return true
        return now - lastReviewedAt > staleAfterDays * ONE_DAY_MILLIS
    }

    fun citationsFor(ruleId: TravelRuleId): List<TravelSourceCitation> {
        val card = ruleCards.firstOrNull { it.ruleId == ruleId } ?: return emptyList()
        return card.citationIds.mapNotNull { citationId ->
            sources.firstOrNull { it.id == citationId }
        }
    }
}

data class TravelChecklistItem(
    val ruleId: TravelRuleId,
    val title: String,
    val status: TravelChecklistStatus,
    val detail: String,
    val citations: List<TravelSourceCitation> = emptyList()
)

data class TravelAssessment(
    val outcome: TravelOutcome,
    val headline: String,
    val summary: String,
    val checklistItems: List<TravelChecklistItem>,
    val computedAt: Long,
    val policyVersion: String,
    val waitTimesUrl: String? = null
)

private const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
