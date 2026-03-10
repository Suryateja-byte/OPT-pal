package com.sidekick.opt_pal.core.compliance

import com.sidekick.opt_pal.core.calculations.UnemploymentAlertThreshold
import com.sidekick.opt_pal.core.calculations.UnemploymentDataQualityState
import com.sidekick.opt_pal.core.calculations.UnemploymentForecast
import com.sidekick.opt_pal.data.model.ComplianceDocumentEvidence
import com.sidekick.opt_pal.data.model.ComplianceEvidenceSnapshot
import com.sidekick.opt_pal.data.model.ComplianceScoreBand
import com.sidekick.opt_pal.data.model.ComplianceScoreQuality
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ComplianceScoreEngineTest {

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun forecast(
        usedDays: Int = 0,
        allowedDays: Int = 90,
        clockRunningNow: Boolean = false,
        currentThreshold: UnemploymentAlertThreshold = UnemploymentAlertThreshold.NONE,
        dataQualityState: UnemploymentDataQualityState = UnemploymentDataQualityState.READY
    ): UnemploymentForecast {
        return UnemploymentForecast(
            usedDays = usedDays,
            remainingDays = (allowedDays - usedDays).coerceAtLeast(0),
            allowedDays = allowedDays,
            clockRunningNow = clockRunningNow,
            currentGapStartDate = null,
            projectedExceedDate = null,
            currentThreshold = currentThreshold,
            dataQualityState = dataQualityState,
            isEstimate = dataQualityState != UnemploymentDataQualityState.READY
        )
    }

    private fun evidence(
        now: Long,
        unemploymentForecast: UnemploymentForecast = forecast(),
        documents: ComplianceDocumentEvidence = ComplianceDocumentEvidence(
            hasPassportDocument = true,
            hasEadDocument = true,
            passportExpirationDate = date(2027, 6, 1),
            eadExpirationDate = date(2026, 12, 31)
        ),
        overdueReporting: List<ReportingObligation> = emptyList(),
        dueSoonReporting: List<ReportingObligation> = emptyList(),
        trackedCase: UscisCaseTracker? = null
    ): ComplianceEvidenceSnapshot {
        return ComplianceEvidenceSnapshot(
            profile = UserProfile(
                uid = "user-1",
                optType = "initial",
                optStartDate = date(2026, 1, 1),
                unemploymentTrackingStartDate = date(2026, 1, 1),
                optEndDate = date(2026, 12, 31)
            ),
            unemploymentForecast = unemploymentForecast,
            documents = documents,
            overdueReportingObligations = overdueReporting,
            dueSoonReportingObligations = dueSoonReporting,
            trackedCase = trackedCase
        )
    }

    @Test
    fun stableEvidenceProducesVerifiedStableScore() {
        val now = date(2026, 3, 10)
        val score = ComplianceScoreEngine { now }.score(
            evidence = evidence(now = now),
            now = now
        )

        assertEquals(ComplianceScoreBand.STABLE, score.band)
        assertEquals(ComplianceScoreQuality.VERIFIED, score.quality)
        assertTrue(score.score >= 90)
        assertTrue(score.blockers.isEmpty())
    }

    @Test
    fun unemploymentOverLimitCapsScoreAtNineteen() {
        val now = date(2026, 3, 10)
        val overLimitForecast = forecast(
            usedDays = 91,
            allowedDays = 90,
            clockRunningNow = true,
            currentThreshold = UnemploymentAlertThreshold.OVER_LIMIT
        )

        val score = ComplianceScoreEngine { now }.score(
            evidence = evidence(now = now, unemploymentForecast = overLimitForecast),
            now = now
        )

        assertEquals(19, score.score)
        assertEquals(ComplianceScoreBand.CRITICAL, score.band)
        assertTrue(score.blockers.any { it.id == "over_unemployment_limit" })
    }

    @Test
    fun overdueReportingCapsScoreAtTwentyNine() {
        val now = date(2026, 3, 10)
        val overdue = ReportingObligation(
            id = "report-1",
            dueDate = date(2026, 2, 20),
            isCompleted = false
        )

        val score = ComplianceScoreEngine { now }.score(
            evidence = evidence(now = now, overdueReporting = listOf(overdue)),
            now = now
        )

        assertEquals(29, score.score)
        assertTrue(score.blockers.any { it.id == "reporting_overdue" })
    }

    @Test
    fun provisionalEvidenceCannotReachPerfectBand() {
        val now = date(2026, 3, 10)
        val provisionalDocuments = ComplianceDocumentEvidence(
            hasPassportDocument = false,
            hasEadDocument = true,
            passportExpirationDate = null,
            eadExpirationDate = date(2026, 12, 31)
        )

        val score = ComplianceScoreEngine { now }.score(
            evidence = evidence(now = now, documents = provisionalDocuments),
            now = now
        )

        assertEquals(ComplianceScoreQuality.PROVISIONAL, score.quality)
        assertTrue(score.score <= 89)
    }

    @Test
    fun expiredVisaDoesNotChangeBaseComplianceScore() {
        val now = date(2026, 3, 10)
        val withoutVisaExpiry = ComplianceScoreEngine { now }.score(
            evidence = evidence(now = now),
            now = now
        )
        val withExpiredVisa = ComplianceScoreEngine { now }.score(
            evidence = evidence(
                now = now,
                documents = ComplianceDocumentEvidence(
                    hasPassportDocument = true,
                    hasEadDocument = true,
                    passportExpirationDate = date(2027, 6, 1),
                    eadExpirationDate = date(2026, 12, 31),
                    visaExpirationDate = date(2025, 5, 1)
                )
            ),
            now = now
        )

        assertEquals(withoutVisaExpiry.score, withExpiredVisa.score)
    }
}
