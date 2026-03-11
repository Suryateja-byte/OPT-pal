package com.sidekick.opt_pal.core.pathway

import com.sidekick.opt_pal.data.model.VisaPathwayEvidenceSnapshot
import com.sidekick.opt_pal.data.model.VisaPathwayH1bRegistrationStatus
import com.sidekick.opt_pal.data.model.VisaPathwayId
import com.sidekick.opt_pal.data.model.VisaPathwayO1EvidenceBucket
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayRecommendation
import com.sidekick.opt_pal.data.model.VisaPathwayH1bSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class VisaPathwayEngineTest {

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun bundle(
        lastReviewedAt: Long = date(2026, 3, 10),
        h1bSeason: VisaPathwayH1bSeason = VisaPathwayH1bSeason(
            fiscalYear = 2027,
            registrationOpenDate = date(2026, 3, 1),
            registrationCloseDate = date(2026, 3, 20),
            isPublished = true
        )
    ) = VisaPathwayPlannerBundle(
        version = "test",
        generatedAt = 0L,
        lastReviewedAt = lastReviewedAt,
        staleAfterDays = 30,
        stemEligibleCipPrefixes = listOf("11."),
        h1bSeason = h1bSeason
    )

    @Test
    fun cleanStemOptScenarioReturnsStrongFit() {
        val assessment = VisaPathwayEngine().assess(
            evidence = VisaPathwayEvidenceSnapshot(
                optType = "initial",
                optEndDate = date(2026, 7, 1),
                cipCode = "11.0701",
                isStemCipEligible = true,
                employerUsesEVerify = true,
                hoursPerWeek = 40,
                isRoleRelatedToDegree = true,
                i983Ready = true
            ),
            bundle = bundle(),
            now = date(2026, 3, 10)
        ).first { it.pathwayId == VisaPathwayId.STEM_OPT }

        assertEquals(VisaPathwayRecommendation.STRONG_FIT, assessment.recommendation)
    }

    @Test
    fun missedH1bSeasonWithoutRegistrationIsNotCurrentFit() {
        val assessment = VisaPathwayEngine().assess(
            evidence = VisaPathwayEvidenceSnapshot(
                isRoleRelatedToDegree = true,
                employerWillSponsorH1b = true,
                desiredContinuityDate = date(2026, 10, 1),
                h1bRegistrationStatus = VisaPathwayH1bRegistrationStatus.NOT_STARTED
            ),
            bundle = bundle(),
            now = date(2026, 4, 10)
        ).first { it.pathwayId == VisaPathwayId.H1B_CAP_SUBJECT }

        assertEquals(VisaPathwayRecommendation.NOT_A_CURRENT_FIT, assessment.recommendation)
        assertTrue(assessment.gaps.any { it.id == "season_closed" })
    }

    @Test
    fun weakO1EvidenceStaysExploratoryOrLower() {
        val assessment = VisaPathwayEngine().assess(
            evidence = VisaPathwayEvidenceSnapshot(
                hasPetitioningEmployerOrAgent = true,
                o1EvidenceSignals = setOf(VisaPathwayO1EvidenceBucket.AWARDS)
            ),
            bundle = bundle(),
            now = date(2026, 3, 10)
        ).first { it.pathwayId == VisaPathwayId.O1A }

        assertTrue(
            assessment.recommendation == VisaPathwayRecommendation.EXPLORATORY ||
                assessment.recommendation == VisaPathwayRecommendation.NOT_A_CURRENT_FIT
        )
    }

    @Test
    fun staleBundleDowngradesStrongPaths() {
        val assessment = VisaPathwayEngine().assess(
            evidence = VisaPathwayEvidenceSnapshot(
                optType = "initial",
                optEndDate = date(2026, 7, 1),
                cipCode = "11.0701",
                isStemCipEligible = true,
                employerUsesEVerify = true,
                hoursPerWeek = 40,
                isRoleRelatedToDegree = true,
                i983Ready = true
            ),
            bundle = bundle(lastReviewedAt = date(2025, 12, 1)),
            now = date(2026, 3, 10)
        ).first { it.pathwayId == VisaPathwayId.STEM_OPT }

        assertEquals(VisaPathwayRecommendation.POSSIBLE_WITH_GAPS, assessment.recommendation)
    }

    @Test
    fun statusIssueEscalatesTemporaryPaths() {
        val assessment = VisaPathwayEngine().assess(
            evidence = VisaPathwayEvidenceSnapshot(
                hasStatusEscalationIssue = true
            ),
            bundle = bundle(),
            now = date(2026, 3, 10)
        ).first { it.pathwayId == VisaPathwayId.H1B_CAP_EXEMPT }

        assertEquals(VisaPathwayRecommendation.CONSULT_DSO_OR_ATTORNEY, assessment.recommendation)
    }
}
