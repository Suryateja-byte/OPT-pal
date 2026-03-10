package com.sidekick.opt_pal.core.travel

import com.sidekick.opt_pal.data.model.TravelAssessment
import com.sidekick.opt_pal.data.model.TravelChecklistItem
import com.sidekick.opt_pal.data.model.TravelChecklistStatus
import com.sidekick.opt_pal.data.model.TravelCountryRestriction
import com.sidekick.opt_pal.data.model.TravelEvidenceSnapshot
import com.sidekick.opt_pal.data.model.TravelOutcome
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import com.sidekick.opt_pal.data.model.TravelRuleId
import com.sidekick.opt_pal.data.model.TravelScenario
import com.sidekick.opt_pal.data.model.TravelTripInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class TravelRulesEngine {

    fun assess(
        tripInput: TravelTripInput,
        evidence: TravelEvidenceSnapshot,
        policyBundle: TravelPolicyBundle,
        now: Long
    ): TravelAssessment {
        val checklist = buildList {
            add(evaluateEscalation(tripInput, policyBundle, now))
            add(evaluateGracePeriod(tripInput, policyBundle))
            add(evaluatePassport(tripInput, policyBundle))
            add(evaluateVisaPath(tripInput, policyBundle))
            add(evaluateI20Signature(tripInput, policyBundle))
            add(evaluateEadStatus(tripInput, policyBundle, now))
            add(evaluateEmploymentEvidence(tripInput, evidence, policyBundle))
            add(evaluateUnemploymentLimit(evidence, policyBundle))
            add(evaluateCountryRestrictions(tripInput, policyBundle))
        }

        val outcome = when {
            checklist.any { it.status == TravelChecklistStatus.ESCALATE } ->
                TravelOutcome.NO_GO_CONTACT_DSO_OR_ATTORNEY
            checklist.any { it.status == TravelChecklistStatus.BLOCK } ->
                TravelOutcome.NO_GO
            checklist.any { it.status == TravelChecklistStatus.CAUTION } ->
                TravelOutcome.CAUTION
            else -> TravelOutcome.GO
        }

        val headline = when (outcome) {
            TravelOutcome.GO -> "Travel looks document-ready."
            TravelOutcome.CAUTION -> "Travel may be possible, but there are real reentry risks."
            TravelOutcome.NO_GO -> "Fix the blocking travel issue before leaving the United States."
            TravelOutcome.NO_GO_CONTACT_DSO_OR_ATTORNEY -> "This trip needs DSO or attorney review before you travel."
        }
        val summary = when (outcome) {
            TravelOutcome.GO ->
                "Your current passport, visa path, I-20, EAD, and employment checks passed the current rules bundle."
            TravelOutcome.CAUTION ->
                "At least one rule requires extra planning, but the trip is not an automatic stop based on the current evidence."
            TravelOutcome.NO_GO ->
                "At least one required travel condition failed. Resolve the blocking item and rerun the assessment."
            TravelOutcome.NO_GO_CONTACT_DSO_OR_ATTORNEY ->
                "A high-risk scenario or stale policy bundle means the app should not give you a definitive travel green light."
        }

        return TravelAssessment(
            outcome = outcome,
            headline = headline,
            summary = summary,
            checklistItems = checklist,
            computedAt = now,
            policyVersion = policyBundle.version,
            waitTimesUrl = policyBundle.sources.firstOrNull { it.id == "dos_wait_times" }?.url
        )
    }

    private fun evaluateEscalation(
        tripInput: TravelTripInput,
        policyBundle: TravelPolicyBundle,
        now: Long
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.ESCALATION)
        return when {
            policyBundle.isStale(now) -> TravelChecklistItem(
                ruleId = TravelRuleId.ESCALATION,
                title = "Policy freshness",
                status = TravelChecklistStatus.ESCALATE,
                detail = "The travel rules bundle is older than ${policyBundle.staleAfterDays} days. Refresh policy guidance and contact your DSO or attorney before relying on this result.",
                citations = citations
            )
            tripInput.capGapActive == true -> TravelChecklistItem(
                ruleId = TravelRuleId.ESCALATION,
                title = "Cap-gap scenario",
                status = TravelChecklistStatus.ESCALATE,
                detail = "Cap-gap travel is a hard-stop scenario in this version of OPTPal. Do not rely on this trip plan without DSO or attorney review.",
                citations = citations
            )
            tripInput.hasRfeStatusIssueOrArrestHistory == true -> TravelChecklistItem(
                ruleId = TravelRuleId.ESCALATION,
                title = "Status or legal issue",
                status = TravelChecklistStatus.ESCALATE,
                detail = "Questions involving RFE responses, status violations, arrests, or other legal complications are escalated out of the app.",
                citations = citations
            )
            else -> TravelChecklistItem(
                ruleId = TravelRuleId.ESCALATION,
                title = "Escalation checks",
                status = TravelChecklistStatus.PASS,
                detail = "No hard-stop escalation trigger was identified from the facts you entered.",
                citations = citations
            )
        }
    }

    private fun evaluateGracePeriod(
        tripInput: TravelTripInput,
        policyBundle: TravelPolicyBundle
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.GRACE_PERIOD)
        return if (tripInput.travelScenario == TravelScenario.GRACE_PERIOD) {
            TravelChecklistItem(
                ruleId = TravelRuleId.GRACE_PERIOD,
                title = "Grace period reentry",
                status = TravelChecklistStatus.BLOCK,
                detail = "The 60-day grace period allows you to remain in the United States after OPT ends, but it is not a travel-and-reentry period.",
                citations = citations
            )
        } else {
            TravelChecklistItem(
                ruleId = TravelRuleId.GRACE_PERIOD,
                title = "Grace period reentry",
                status = TravelChecklistStatus.PASS,
                detail = "You are not relying on grace-period reentry for this trip.",
                citations = citations
            )
        }
    }

    private fun evaluatePassport(
        tripInput: TravelTripInput,
        policyBundle: TravelPolicyBundle
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.PASSPORT_VALIDITY)
        val returnDate = tripInput.plannedReturnDate
        val passportExpiry = tripInput.passportExpirationDate
        val passportCountry = tripInput.passportIssuingCountry
        if (returnDate == null || passportExpiry == null || passportCountry.isBlank()) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.PASSPORT_VALIDITY,
                title = "Passport validity",
                status = TravelChecklistStatus.BLOCK,
                detail = "Add your passport issuing country and expiration date before relying on a travel result.",
                citations = citations
            )
        }

        val returnLocalDate = returnDate.toUtcLocalDate()
        val passportLocalDate = passportExpiry.toUtcLocalDate()
        if (passportLocalDate.isBefore(returnLocalDate)) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.PASSPORT_VALIDITY,
                title = "Passport validity",
                status = TravelChecklistStatus.BLOCK,
                detail = "Your passport expires before the planned return date.",
                citations = citations
            )
        }

        val sixMonthThreshold = returnLocalDate.plusMonths(
            policyBundle.passportValidity.defaultAdditionalValidityMonths.toLong()
        )
        if (!passportLocalDate.isBefore(sixMonthThreshold)) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.PASSPORT_VALIDITY,
                title = "Passport validity",
                status = TravelChecklistStatus.PASS,
                detail = "Your passport is valid through the return date and beyond the standard six-month threshold.",
                citations = citations
            )
        }

        val sixMonthClub = policyBundle.passportValidity.sixMonthClubCountries
            .map(::normalizeCountry)
            .toSet()
        return if (sixMonthClub.contains(normalizeCountry(passportCountry))) {
            TravelChecklistItem(
                ruleId = TravelRuleId.PASSPORT_VALIDITY,
                title = "Passport validity",
                status = TravelChecklistStatus.PASS,
                detail = "Your passport is valid through the return date, and the issuing country is on the CBP six-month-club exemption list.",
                citations = citations
            )
        } else {
            TravelChecklistItem(
                ruleId = TravelRuleId.PASSPORT_VALIDITY,
                title = "Passport validity",
                status = TravelChecklistStatus.CAUTION,
                detail = "Your passport is valid through the return date but not six months beyond it. Verify whether your passport nationality is exempt before you travel.",
                citations = citations
            )
        }
    }

    private fun evaluateVisaPath(
        tripInput: TravelTripInput,
        policyBundle: TravelPolicyBundle
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.VISA_PATH)
        val returnDate = tripInput.plannedReturnDate
        val visaExpiry = tripInput.visaExpirationDate
        val visaClass = tripInput.visaClass.trim()
        if (returnDate == null) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.VISA_PATH,
                title = "Visa path",
                status = TravelChecklistStatus.BLOCK,
                detail = "Add a planned return date to determine whether the visa path is valid.",
                citations = citations
            )
        }

        val visaValidOnReturn = visaExpiry != null &&
            !visaExpiry.toUtcLocalDate().isBefore(returnDate.toUtcLocalDate()) &&
            visaClass.equals("F-1", ignoreCase = true)
        if (visaValidOnReturn) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.VISA_PATH,
                title = "Visa path",
                status = TravelChecklistStatus.PASS,
                detail = "Your current F-1 visa remains valid through the planned return date.",
                citations = citations
            )
        }

        if (qualifiesForAutomaticRevalidation(tripInput, policyBundle)) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.VISA_PATH,
                title = "Visa path",
                status = TravelChecklistStatus.CAUTION,
                detail = "This trip may qualify for automatic visa revalidation because it is limited to Canada, Mexico, or adjacent islands for fewer than ${policyBundle.automaticRevalidation.maxTripLengthDays} days and does not rely on a new visa application.",
                citations = citations
            )
        }

        if (tripInput.needsNewVisa == true && tripInput.visaRenewalOutsideResidence == false) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.VISA_PATH,
                title = "Visa path",
                status = TravelChecklistStatus.CAUTION,
                detail = "You will need a new F-1 visa appointment. Plan around official visa wait times and only schedule in your country of nationality or residence.",
                citations = citations
            )
        }

        if (tripInput.needsNewVisa == true && tripInput.visaRenewalOutsideResidence == true) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.VISA_PATH,
                title = "Visa path",
                status = TravelChecklistStatus.BLOCK,
                detail = "Current Department of State guidance says nonimmigrant visa applicants should interview in their country of nationality or residence. This trip relies on a more fragile visa-renewal path.",
                citations = citations
            )
        }

        return TravelChecklistItem(
            ruleId = TravelRuleId.VISA_PATH,
            title = "Visa path",
            status = TravelChecklistStatus.BLOCK,
            detail = "Your current facts do not show a valid F-1 visa on return or a qualifying automatic-revalidation path.",
            citations = citations
        )
    }

    private fun evaluateI20Signature(
        tripInput: TravelTripInput,
        policyBundle: TravelPolicyBundle
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.I20_SIGNATURE)
        val returnDate = tripInput.plannedReturnDate
        val signatureDate = tripInput.i20TravelSignatureDate
        if (returnDate == null || signatureDate == null) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.I20_SIGNATURE,
                title = "I-20 travel signature",
                status = TravelChecklistStatus.BLOCK,
                detail = "Add the date of your most recent I-20 travel signature before relying on this trip result.",
                citations = citations
            )
        }

        val signatureLocalDate = signatureDate.toUtcLocalDate()
        val threshold = returnDate.toUtcLocalDate().minusMonths(6)
        return if (signatureLocalDate.isBefore(threshold)) {
            TravelChecklistItem(
                ruleId = TravelRuleId.I20_SIGNATURE,
                title = "I-20 travel signature",
                status = TravelChecklistStatus.BLOCK,
                detail = "Your I-20 travel signature will be older than six months on the planned return date.",
                citations = citations
            )
        } else {
            TravelChecklistItem(
                ruleId = TravelRuleId.I20_SIGNATURE,
                title = "I-20 travel signature",
                status = TravelChecklistStatus.PASS,
                detail = "Your I-20 travel signature is recent enough for OPT reentry guidance.",
                citations = citations
            )
        }
    }

    private fun evaluateEadStatus(
        tripInput: TravelTripInput,
        policyBundle: TravelPolicyBundle,
        now: Long
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.EAD_STATUS)
        val scenario = tripInput.travelScenario
        val returnDate = tripInput.plannedReturnDate
        return when (scenario) {
            TravelScenario.PENDING_INITIAL_OPT -> TravelChecklistItem(
                ruleId = TravelRuleId.EAD_STATUS,
                title = "OPT authorization status",
                status = TravelChecklistStatus.CAUTION,
                detail = "Initial OPT is still pending. Travel may be possible, but reentry remains discretionary and is higher risk without a final OPT approval in hand.",
                citations = citations
            )
            TravelScenario.APPROVED_POST_COMPLETION_OPT,
            TravelScenario.APPROVED_STEM_OPT -> {
                if (tripInput.hasOriginalEadInHand != true || tripInput.eadExpirationDate == null || returnDate == null) {
                    TravelChecklistItem(
                        ruleId = TravelRuleId.EAD_STATUS,
                        title = "EAD status",
                        status = TravelChecklistStatus.BLOCK,
                        detail = "Approved OPT travel requires the original EAD and a valid card end date.",
                        citations = citations
                    )
                } else if (tripInput.eadExpirationDate.toUtcLocalDate().isBefore(returnDate.toUtcLocalDate())) {
                    TravelChecklistItem(
                        ruleId = TravelRuleId.EAD_STATUS,
                        title = "EAD status",
                        status = TravelChecklistStatus.BLOCK,
                        detail = "Your EAD expires before the planned return date.",
                        citations = citations
                    )
                } else {
                    TravelChecklistItem(
                        ruleId = TravelRuleId.EAD_STATUS,
                        title = "EAD status",
                        status = TravelChecklistStatus.PASS,
                        detail = "Your EAD is in hand and valid through the planned return date.",
                        citations = citations
                    )
                }
            }
            TravelScenario.PENDING_STEM_EXTENSION -> {
                val eadExpiry = tripInput.eadExpirationDate
                if (eadExpiry == null) {
                    TravelChecklistItem(
                        ruleId = TravelRuleId.EAD_STATUS,
                        title = "STEM extension status",
                        status = TravelChecklistStatus.ESCALATE,
                        detail = "A pending STEM extension requires verifying the original EAD end date before travel.",
                        citations = citations
                    )
                } else if (eadExpiry < now || (returnDate != null && eadExpiry.toUtcLocalDate().isBefore(returnDate.toUtcLocalDate()))) {
                    TravelChecklistItem(
                        ruleId = TravelRuleId.EAD_STATUS,
                        title = "STEM extension status",
                        status = TravelChecklistStatus.ESCALATE,
                        detail = "The original EAD appears expired or will expire before reentry while the STEM extension remains pending. Treat this as a hard-stop scenario.",
                        citations = citations
                    )
                } else {
                    TravelChecklistItem(
                        ruleId = TravelRuleId.EAD_STATUS,
                        title = "STEM extension status",
                        status = TravelChecklistStatus.CAUTION,
                        detail = "A pending STEM extension is travel-sensitive even before the original EAD expires. Carry the original EAD, the STEM I-20, and employment proof.",
                        citations = citations
                    )
                }
            }
            TravelScenario.GRACE_PERIOD -> TravelChecklistItem(
                ruleId = TravelRuleId.EAD_STATUS,
                title = "EAD status",
                status = TravelChecklistStatus.BLOCK,
                detail = "OPT grace-period travel does not support reentry in F-1 status.",
                citations = citations
            )
            null -> TravelChecklistItem(
                ruleId = TravelRuleId.EAD_STATUS,
                title = "OPT authorization status",
                status = TravelChecklistStatus.BLOCK,
                detail = "Choose your current OPT travel scenario before relying on this assessment.",
                citations = citations
            )
        }
    }

    private fun evaluateEmploymentEvidence(
        tripInput: TravelTripInput,
        evidence: TravelEvidenceSnapshot,
        policyBundle: TravelPolicyBundle
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.EMPLOYMENT_EVIDENCE)
        val scenario = tripInput.travelScenario
        val needsProof = scenario in setOf(
            TravelScenario.APPROVED_POST_COMPLETION_OPT,
            TravelScenario.APPROVED_STEM_OPT,
            TravelScenario.PENDING_STEM_EXTENSION
        )
        val hasProof = tripInput.hasEmploymentOrOfferProof ?: evidence.hasCurrentEmploymentRecord
        return if (needsProof && hasProof != true) {
            TravelChecklistItem(
                ruleId = TravelRuleId.EMPLOYMENT_EVIDENCE,
                title = "Employment or offer proof",
                status = TravelChecklistStatus.BLOCK,
                detail = "Approved OPT or STEM travel should be backed by active employment or a current job-offer letter for reentry questions.",
                citations = citations
            )
        } else {
            TravelChecklistItem(
                ruleId = TravelRuleId.EMPLOYMENT_EVIDENCE,
                title = "Employment or offer proof",
                status = TravelChecklistStatus.PASS,
                detail = if (needsProof) {
                    "You reported current employment or offer proof for reentry."
                } else {
                    "This travel scenario does not depend on current employment proof for the base assessment."
                },
                citations = citations
            )
        }
    }

    private fun evaluateUnemploymentLimit(
        evidence: TravelEvidenceSnapshot,
        policyBundle: TravelPolicyBundle
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.UNEMPLOYMENT_LIMIT)
        return if (evidence.unemploymentDaysUsed >= evidence.unemploymentDaysAllowed) {
            TravelChecklistItem(
                ruleId = TravelRuleId.UNEMPLOYMENT_LIMIT,
                title = "Unemployment limit",
                status = TravelChecklistStatus.BLOCK,
                detail = "Your recorded unemployment count is already at or above the allowed limit for the current OPT cycle.",
                citations = citations
            )
        } else {
            TravelChecklistItem(
                ruleId = TravelRuleId.UNEMPLOYMENT_LIMIT,
                title = "Unemployment limit",
                status = TravelChecklistStatus.PASS,
                detail = "Your recorded unemployment total is ${evidence.unemploymentDaysUsed} of ${evidence.unemploymentDaysAllowed} allowed days.",
                citations = citations
            )
        }
    }

    private fun evaluateCountryRestrictions(
        tripInput: TravelTripInput,
        policyBundle: TravelPolicyBundle
    ): TravelChecklistItem {
        val citations = policyBundle.citationsFor(TravelRuleId.COUNTRY_RESTRICTIONS)
        if (tripInput.needsNewVisa != true) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.COUNTRY_RESTRICTIONS,
                title = "Current visa-issuance overlays",
                status = TravelChecklistStatus.PASS,
                detail = "No country-based visa-issuance overlay is triggered because this plan does not rely on a new F-1 visa appointment.",
                citations = citations
            )
        }

        val passportCountry = normalizeCountry(tripInput.passportIssuingCountry)
        val matchedRestriction = policyBundle.countryRestrictions.firstOrNull { restriction ->
            normalizeCountry(restriction.nationality) == passportCountry &&
                restriction.visaClasses.any { it.equals("F-1", ignoreCase = true) || it.equals("ALL", ignoreCase = true) }
        }
        if (matchedRestriction != null) {
            return TravelChecklistItem(
                ruleId = TravelRuleId.COUNTRY_RESTRICTIONS,
                title = "Current visa-issuance overlays",
                status = TravelChecklistStatus.BLOCK,
                detail = buildCountryRestrictionDetail(matchedRestriction),
                citations = citations
            )
        }

        return TravelChecklistItem(
            ruleId = TravelRuleId.COUNTRY_RESTRICTIONS,
            title = "Current visa-issuance overlays",
            status = TravelChecklistStatus.CAUTION,
            detail = "No nationality-based suspension was matched, but any new F-1 visa appointment still depends on consular scheduling and current State Department guidance.",
            citations = citations
        )
    }

    private fun qualifiesForAutomaticRevalidation(
        tripInput: TravelTripInput,
        policyBundle: TravelPolicyBundle
    ): Boolean {
        if (tripInput.onlyCanadaMexicoAdjacentIslands != true) return false
        if (tripInput.needsNewVisa == true) return false
        if (tripInput.visaRenewalOutsideResidence == true) return false
        val daysAbroad = tripInput.daysAbroad ?: return false
        if (daysAbroad >= policyBundle.automaticRevalidation.maxTripLengthDays) return false
        val destination = normalizeCountry(tripInput.destinationCountry)
        val eligibleCountries = (policyBundle.automaticRevalidation.allowedCountries +
            policyBundle.automaticRevalidation.adjacentIslands)
            .map(::normalizeCountry)
            .toSet()
        val excludedCountries = policyBundle.automaticRevalidation.excludedCountries
            .map(::normalizeCountry)
            .toSet()
        return destination.isNotBlank() &&
            eligibleCountries.contains(destination) &&
            !excludedCountries.contains(destination)
    }

    private fun buildCountryRestrictionDetail(restriction: TravelCountryRestriction): String {
        return if (restriction.isFullSuspension) {
            "${restriction.nationality} is currently subject to a full or near-full visa-issuance suspension for this travel path. Do not rely on a new F-1 visa appointment without updated legal guidance."
        } else {
            restriction.summary
        }
    }

    private fun Long.toUtcLocalDate(): LocalDate {
        return Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
    }

    private fun normalizeCountry(value: String): String {
        return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
    }
}
