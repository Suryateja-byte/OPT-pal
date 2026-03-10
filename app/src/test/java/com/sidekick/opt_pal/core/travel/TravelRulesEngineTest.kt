package com.sidekick.opt_pal.core.travel

import com.sidekick.opt_pal.data.model.TravelAutomaticRevalidationPolicy
import com.sidekick.opt_pal.data.model.TravelChecklistStatus
import com.sidekick.opt_pal.data.model.TravelEvidenceSnapshot
import com.sidekick.opt_pal.data.model.TravelOutcome
import com.sidekick.opt_pal.data.model.TravelPassportValidityPolicy
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import com.sidekick.opt_pal.data.model.TravelPolicyRuleCard
import com.sidekick.opt_pal.data.model.TravelRuleId
import com.sidekick.opt_pal.data.model.TravelScenario
import com.sidekick.opt_pal.data.model.TravelSourceCitation
import com.sidekick.opt_pal.data.model.TravelTripInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TravelRulesEngineTest {

    private val engine = TravelRulesEngine()
    private val now = date(2026, 3, 10)

    @Test
    fun cleanApprovedOptTripIsGo() {
        val assessment = engine.assess(
            tripInput = baseTripInput(),
            evidence = baseEvidence(),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.GO, assessment.outcome)
    }

    @Test
    fun expiredI20SignatureBlocksTravel() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(i20TravelSignatureDate = date(2025, 7, 1)),
            evidence = baseEvidence(),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.NO_GO, assessment.outcome)
        assertTrue(assessment.checklistItems.any { it.ruleId == TravelRuleId.I20_SIGNATURE && it.status == TravelChecklistStatus.BLOCK })
    }

    @Test
    fun approvedOptWithoutEmploymentIsNoGo() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(hasEmploymentOrOfferProof = false),
            evidence = baseEvidence(hasCurrentEmploymentRecord = false),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.NO_GO, assessment.outcome)
    }

    @Test
    fun gracePeriodTravelIsNoGo() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(travelScenario = TravelScenario.GRACE_PERIOD),
            evidence = baseEvidence(),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.NO_GO, assessment.outcome)
    }

    @Test
    fun pendingInitialOptWithoutEadIsCaution() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(
                travelScenario = TravelScenario.PENDING_INITIAL_OPT,
                hasOriginalEadInHand = false,
                eadExpirationDate = null,
                hasEmploymentOrOfferProof = false
            ),
            evidence = baseEvidence(hasCurrentEmploymentRecord = false),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.CAUTION, assessment.outcome)
    }

    @Test
    fun automaticRevalidationReturnsCaution() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(
                destinationCountry = "Canada",
                onlyCanadaMexicoAdjacentIslands = true,
                needsNewVisa = false,
                visaRenewalOutsideResidence = false,
                visaExpirationDate = date(2026, 4, 1)
            ),
            evidence = baseEvidence(),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.CAUTION, assessment.outcome)
    }

    @Test
    fun automaticRevalidationDisqualifierIsNoGo() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(
                destinationCountry = "Canada",
                onlyCanadaMexicoAdjacentIslands = true,
                needsNewVisa = true,
                visaRenewalOutsideResidence = true,
                visaExpirationDate = date(2026, 4, 1)
            ),
            evidence = baseEvidence(),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.NO_GO, assessment.outcome)
    }

    @Test
    fun capGapHardStopEscalates() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(capGapActive = true),
            evidence = baseEvidence(),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.NO_GO_CONTACT_DSO_OR_ATTORNEY, assessment.outcome)
    }

    @Test
    fun unemploymentLimitHardStopBlocks() {
        val assessment = engine.assess(
            tripInput = baseTripInput(),
            evidence = baseEvidence(unemploymentDaysUsed = 90, unemploymentDaysAllowed = 90),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.NO_GO, assessment.outcome)
    }

    @Test
    fun visaRenewalPlusCountryRestrictionBlocks() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(
                needsNewVisa = true,
                visaRenewalOutsideResidence = false,
                passportIssuingCountry = "Nigeria",
                visaExpirationDate = date(2026, 4, 1)
            ),
            evidence = baseEvidence(),
            policyBundle = policyBundle(countryRestrictions = listOf(countryRestriction("Nigeria", false))),
            now = now
        )

        assertEquals(TravelOutcome.NO_GO, assessment.outcome)
    }

    @Test
    fun pendingStemBeforeExpiryIsCaution() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(
                travelScenario = TravelScenario.PENDING_STEM_EXTENSION,
                eadExpirationDate = date(2026, 6, 30)
            ),
            evidence = baseEvidence(),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.CAUTION, assessment.outcome)
    }

    @Test
    fun pendingStemAfterExpiryEscalates() {
        val assessment = engine.assess(
            tripInput = baseTripInput().copy(
                travelScenario = TravelScenario.PENDING_STEM_EXTENSION,
                eadExpirationDate = date(2026, 4, 1)
            ),
            evidence = baseEvidence(),
            policyBundle = policyBundle(),
            now = now
        )

        assertEquals(TravelOutcome.NO_GO_CONTACT_DSO_OR_ATTORNEY, assessment.outcome)
    }

    private fun baseTripInput(): TravelTripInput {
        return TravelTripInput(
            departureDate = date(2026, 4, 15),
            plannedReturnDate = date(2026, 4, 25),
            destinationCountry = "India",
            onlyCanadaMexicoAdjacentIslands = false,
            needsNewVisa = false,
            visaRenewalOutsideResidence = false,
            hasEmploymentOrOfferProof = true,
            capGapActive = false,
            hasRfeStatusIssueOrArrestHistory = false,
            travelScenario = TravelScenario.APPROVED_POST_COMPLETION_OPT,
            passportIssuingCountry = "India",
            passportExpirationDate = date(2027, 1, 1),
            visaClass = "F-1",
            visaExpirationDate = date(2026, 12, 31),
            i20TravelSignatureDate = date(2026, 2, 1),
            eadExpirationDate = date(2026, 12, 31),
            hasOriginalEadInHand = true
        )
    }

    private fun baseEvidence(
        hasCurrentEmploymentRecord: Boolean = true,
        unemploymentDaysUsed: Int = 10,
        unemploymentDaysAllowed: Int = 90
    ): TravelEvidenceSnapshot {
        return TravelEvidenceSnapshot(
            hasCurrentEmploymentRecord = hasCurrentEmploymentRecord,
            unemploymentDaysUsed = unemploymentDaysUsed,
            unemploymentDaysAllowed = unemploymentDaysAllowed
        )
    }

    private fun policyBundle(
        countryRestrictions: List<com.sidekick.opt_pal.data.model.TravelCountryRestriction> = emptyList()
    ): TravelPolicyBundle {
        val citation = TravelSourceCitation(
            id = "source-1",
            label = "Official source",
            url = "https://example.com",
            effectiveDate = "2026-01-01",
            lastReviewedDate = "2026-03-10"
        )
        return TravelPolicyBundle(
            version = "test",
            generatedAt = now,
            lastReviewedAt = now,
            staleAfterDays = 30,
            sources = listOf(citation),
            ruleCards = TravelRuleId.entries.map { ruleId ->
                TravelPolicyRuleCard(
                    ruleId = ruleId,
                    title = ruleId.name,
                    summary = ruleId.name,
                    citationIds = listOf("source-1")
                )
            },
            passportValidity = TravelPassportValidityPolicy(
                defaultAdditionalValidityMonths = 6,
                sixMonthClubCountries = listOf("India", "Canada", "Nigeria")
            ),
            automaticRevalidation = TravelAutomaticRevalidationPolicy(
                allowedCountries = listOf("Canada", "Mexico"),
                adjacentIslands = listOf("Bermuda"),
                excludedCountries = listOf("Cuba"),
                maxTripLengthDays = 30,
                summary = "test"
            ),
            countryRestrictions = countryRestrictions
        )
    }

    private fun countryRestriction(
        nationality: String,
        isFullSuspension: Boolean
    ) = com.sidekick.opt_pal.data.model.TravelCountryRestriction(
        nationality = nationality,
        summary = "$nationality restricted",
        visaClasses = listOf("F-1"),
        appliesToNewVisaOnly = true,
        isFullSuspension = isFullSuspension
    )

    private fun date(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }
}
