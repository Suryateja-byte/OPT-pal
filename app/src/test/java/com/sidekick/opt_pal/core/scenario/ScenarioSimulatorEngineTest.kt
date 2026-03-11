package com.sidekick.opt_pal.core.scenario

import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.H1bCapSeason
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bProfile
import com.sidekick.opt_pal.data.model.H1bWorkflowStage
import com.sidekick.opt_pal.data.model.JobLossScenarioAssumptions
import com.sidekick.opt_pal.data.model.PendingStemExtensionScenarioAssumptions
import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.ScenarioOutcome
import com.sidekick.opt_pal.data.model.ScenarioSimulatorBundle
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.H1bContinuityScenarioAssumptions
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ScenarioSimulatorEngineTest {

    private val engine = ScenarioSimulatorEngine()

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun scenarioBundle(now: Long) = ScenarioSimulatorBundle(
        version = "test",
        generatedAt = now,
        lastReviewedAt = now,
        staleAfterDays = 30
    )

    private fun h1bBundle(now: Long) = H1bDashboardBundle(
        version = "test",
        generatedAt = now,
        lastReviewedAt = now,
        capSeason = H1bCapSeason(
            fiscalYear = 2027,
            registrationOpenAt = date(2026, 3, 4),
            registrationCloseAt = date(2026, 3, 19),
            petitionFilingOpensAt = date(2026, 4, 1)
        )
    )

    private fun pathwayBundle(now: Long) = VisaPathwayPlannerBundle(
        version = "test",
        generatedAt = now,
        lastReviewedAt = now
    )

    @Test
    fun jobLossCrossingAllowedDaysBecomesHighRisk() {
        val now = date(2026, 7, 15)
        val result = engine.simulate(
            draft = ScenarioDraft(
                assumptions = JobLossScenarioAssumptions(
                    interruptionStartDate = date(2026, 4, 1)
                )
            ),
            baseline = ScenarioBaselineSnapshot(
                userProfile = UserProfile(
                    optType = "initial",
                    optStartDate = date(2026, 1, 1),
                    unemploymentTrackingStartDate = date(2026, 1, 1),
                    optEndDate = date(2026, 12, 31)
                ),
                employments = listOf(
                    Employment(
                        id = "active",
                        employerName = "Current Employer",
                        startDate = date(2026, 1, 1),
                        endDate = null,
                        jobTitle = "Engineer",
                        hoursPerWeek = 40
                    )
                ),
                scenarioBundle = scenarioBundle(now)
            ),
            now = now
        )

        assertEquals(ScenarioOutcome.HIGH_RISK, result.outcome)
        assertTrue(result.headline.contains("compliance risk", ignoreCase = true))
    }

    @Test
    fun capGapTravelEscalatesToConsult() {
        val now = date(2026, 4, 15)
        val result = engine.simulate(
            draft = ScenarioDraft(
                assumptions = H1bContinuityScenarioAssumptions(
                    workflowStage = H1bWorkflowStage.PETITION_FILED.wireValue,
                    selectedRegistration = true,
                    filedPetition = true,
                    requestedChangeOfStatus = true,
                    travelPlanned = true,
                    hasReceiptNotice = true
                )
            ),
            baseline = ScenarioBaselineSnapshot(
                userProfile = UserProfile(
                    optType = "initial",
                    optEndDate = date(2026, 5, 31)
                ),
                h1bProfile = H1bProfile(selfReportedSponsorIntent = true),
                h1bBundle = h1bBundle(now),
                scenarioBundle = scenarioBundle(now)
            ),
            now = now
        )

        assertEquals(ScenarioOutcome.CONSULT_DSO_OR_ATTORNEY, result.outcome)
        assertTrue(result.summary.contains("travel", ignoreCase = true))
    }

    @Test
    fun timelyStemExtensionUses180DayBridge() {
        val now = date(2026, 5, 1)
        val optEndDate = date(2026, 6, 1)
        val result = engine.simulate(
            draft = ScenarioDraft(
                assumptions = PendingStemExtensionScenarioAssumptions(
                    filingDate = date(2026, 5, 20),
                    optEndDate = optEndDate,
                    hasReceiptNotice = true,
                    travelPlanned = false,
                    employerChangePlanned = false,
                    hasNewI983 = true,
                    hasNewI20 = true
                )
            ),
            baseline = ScenarioBaselineSnapshot(
                userProfile = UserProfile(
                    optType = "stem",
                    optEndDate = optEndDate
                ),
                pathwayBundle = pathwayBundle(now),
                scenarioBundle = scenarioBundle(now)
            ),
            now = now
        )

        val bridgeEvent = result.timeline.firstOrNull { it.id == "bridge_end" }
        assertEquals(ScenarioOutcome.ON_TRACK, result.outcome)
        assertEquals(optEndDate + 180L * 24L * 60L * 60L * 1000L, bridgeEvent?.timestamp)
        assertTrue(result.summary.contains("180-day", ignoreCase = true))
    }
}
