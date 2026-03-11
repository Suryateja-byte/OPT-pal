package com.sidekick.opt_pal.core.h1b

import com.sidekick.opt_pal.data.model.H1bCapGapState
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bEmployerFiscalYearRecord
import com.sidekick.opt_pal.data.model.H1bEmployerHistorySummary
import com.sidekick.opt_pal.data.model.H1bEmployerVerification
import com.sidekick.opt_pal.data.model.H1bEvidence
import com.sidekick.opt_pal.data.model.H1bEVerifyStatus
import com.sidekick.opt_pal.data.model.H1bProfile
import com.sidekick.opt_pal.data.model.H1bTimelineState
import com.sidekick.opt_pal.data.model.H1bVerificationConfidence
import com.sidekick.opt_pal.data.model.H1bWorkflowStage
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayEmployerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class H1bDashboardEngineTest {

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun bundle() = H1bDashboardBundle(
        version = "test",
        generatedAt = 0L,
        lastReviewedAt = date(2026, 3, 11),
        capSeason = com.sidekick.opt_pal.data.model.H1bCapSeason(
            fiscalYear = 2027,
            registrationOpenAt = date(2026, 3, 4),
            registrationCloseAt = date(2026, 3, 19),
            petitionFilingOpensAt = date(2026, 4, 1)
        )
    )

    @Test
    fun universityEmployerWithSavedSignalsBecomesVerified() {
        val state = H1bDashboardEngine().build(
            userProfile = UserProfile(optEndDate = date(2026, 6, 1)),
            h1bProfile = H1bProfile(
                employerName = "State University",
                employerType = VisaPathwayEmployerType.UNIVERSITY.wireValue,
                selfReportedSponsorIntent = true,
                roleMatchesSpecialtyOccupation = true
            ),
            employerVerification = H1bEmployerVerification(
                eVerifyStatus = H1bEVerifyStatus.ACTIVE.wireValue,
                eVerifyUserConfirmed = true,
                employerHistory = H1bEmployerHistorySummary(
                    matchedEmployerName = "State University",
                    fiscalYearSummaries = listOf(
                        H1bEmployerFiscalYearRecord(fiscalYear = 2025, initialApprovals = 4)
                    )
                )
            ),
            timelineState = H1bTimelineState(workflowStage = H1bWorkflowStage.REGISTRATION_PLANNED.wireValue),
            caseTracking = com.sidekick.opt_pal.data.model.H1bCaseTracking(),
            evidence = H1bEvidence(hasCapExemptSupport = true),
            trackedCases = emptyList(),
            bundle = bundle(),
            now = date(2026, 3, 11)
        )

        assertEquals(H1bVerificationConfidence.VERIFIED, state.readinessSummary.verificationConfidence)
        assertTrue(state.employerVerificationSummary.capTrackDetail.contains("university", ignoreCase = true))
    }

    @Test
    fun capGapTravelCreatesHardStop() {
        val state = H1bDashboardEngine().build(
            userProfile = UserProfile(optEndDate = date(2026, 5, 30)),
            h1bProfile = H1bProfile(selfReportedSponsorIntent = true),
            employerVerification = H1bEmployerVerification(),
            timelineState = H1bTimelineState(
                workflowStage = H1bWorkflowStage.PETITION_FILED.wireValue,
                requestedChangeOfStatus = true
            ),
            caseTracking = com.sidekick.opt_pal.data.model.H1bCaseTracking(),
            evidence = H1bEvidence(capGapTravelPlanned = true),
            trackedCases = emptyList(),
            bundle = bundle(),
            now = date(2026, 4, 15)
        )

        assertEquals(H1bCapGapState.HARD_STOP, state.capGapAssessment.state)
        assertTrue(state.capGapAssessment.legalReviewRequired)
        assertEquals("H-1B path needs human review", state.readinessSummary.title)
    }

    @Test
    fun linkedI129CaseIsSelectedFromTrackedCases() {
        val tracker = UscisCaseTracker(
            id = "WAC1234567890",
            receiptNumber = "WAC1234567890",
            formType = "I-129",
            normalizedStage = "RECEIVED",
            officialStatusText = "Case Was Received"
        )

        val state = H1bDashboardEngine().build(
            userProfile = UserProfile(optEndDate = date(2026, 6, 1)),
            h1bProfile = H1bProfile(),
            employerVerification = H1bEmployerVerification(),
            timelineState = H1bTimelineState(receiptNumber = "WAC1234567890"),
            caseTracking = com.sidekick.opt_pal.data.model.H1bCaseTracking(linkedReceiptNumber = "WAC1234567890"),
            evidence = H1bEvidence(),
            trackedCases = listOf(tracker),
            bundle = bundle(),
            now = date(2026, 3, 11)
        )

        assertEquals("WAC1234567890", state.caseTrackingState.linkedReceiptNumber)
        assertEquals("I-129", state.caseTrackingState.linkedCase?.formType)
    }
}
