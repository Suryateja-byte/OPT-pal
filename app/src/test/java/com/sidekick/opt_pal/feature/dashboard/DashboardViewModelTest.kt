package com.sidekick.opt_pal.feature.dashboard

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.PeerBenchmarkCard
import com.sidekick.opt_pal.data.model.PeerDataSnapshot
import com.sidekick.opt_pal.data.model.PolicyAlertAvailability
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertSource
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ScenarioDraft
import com.sidekick.opt_pal.data.model.ScenarioDraftOutcomeSummary
import com.sidekick.opt_pal.data.model.ScenarioOutcome
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakeCaseStatusRepository
import com.sidekick.opt_pal.testing.fakes.FakeComplianceHealthRepository
import com.sidekick.opt_pal.testing.fakes.FakeDashboardRepository
import com.sidekick.opt_pal.testing.fakes.FakeDocumentRepository
import com.sidekick.opt_pal.testing.fakes.FakePeerDataRepository
import com.sidekick.opt_pal.testing.fakes.FakePolicyAlertRepository
import com.sidekick.opt_pal.testing.fakes.FakeReportingRepository
import com.sidekick.opt_pal.testing.fakes.FakeScenarioSimulatorRepository
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun combinesProfileEmploymentAndReporting() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val documentRepository = FakeDocumentRepository()
        val complianceHealthRepository = FakeComplianceHealthRepository()
        val now = date(2024, 1, 20)
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            caseStatusRepository,
            policyAlertRepository,
            documentRepository,
            complianceHealthRepository
        ) { now }

        val firebaseUser = mockUser("user-1")
        authRepository.emitUser(firebaseUser)
        val optStart = date(2024, 1, 1)
        authRepository.emitProfile(
            "user-1",
            UserProfile(uid = "user-1", email = "alex@example.com", optType = "stem", optStartDate = optStart)
        )
        dashboardRepository.setEmployments(
            listOf(
                Employment(
                    id = "job-1",
                    employerName = "Acme",
                    jobTitle = "Engineer",
                    startDate = date(2024, 1, 5),
                    endDate = null,
                    hoursPerWeek = 40
                )
            )
        )
        reportingRepository.setObligations(
            listOf(
                ReportingObligation(id = "task-1", dueDate = date(2024, 2, 1), isCompleted = false),
                ReportingObligation(id = "task-2", dueDate = date(2024, 1, 15), isCompleted = true)
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Alex", state.displayName)
        assertEquals("STEM Extension", state.optLabel)
        assertEquals(optStart, state.optStartDate)
        assertEquals(4, state.unemploymentDaysUsed)
        assertEquals(150, state.unemploymentDaysAllowed)
        assertFalse(state.clockRunningNow)
        assertEquals(1, state.pendingReportingCount)
        assertEquals(date(2024, 2, 1), state.nextReportingDue)
    }

    @Test
    fun deleteEmploymentPassesIdToRepository() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val documentRepository = FakeDocumentRepository()
        val complianceHealthRepository = FakeComplianceHealthRepository()
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            caseStatusRepository,
            policyAlertRepository,
            documentRepository,
            complianceHealthRepository
        )
        val firebaseUser = mockUser("user-2")
        authRepository.emitUser(firebaseUser)
        advanceUntilIdle()

        viewModel.deleteEmployment("job-22")
        advanceUntilIdle()

        assertTrue(dashboardRepository.deletedEmploymentIds.contains("job-22"))
    }

    @Test
    fun missingHoursShowsReviewState() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val documentRepository = FakeDocumentRepository()
        val complianceHealthRepository = FakeComplianceHealthRepository()
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            caseStatusRepository,
            policyAlertRepository,
            documentRepository,
            complianceHealthRepository
        ) { date(2024, 2, 1) }

        val firebaseUser = mockUser("user-3")
        authRepository.emitUser(firebaseUser)
        authRepository.emitProfile(
            "user-3",
            UserProfile(uid = "user-3", email = "test@example.com", optType = "initial", optStartDate = date(2024, 1, 1))
        )
        dashboardRepository.setEmployments(
            listOf(
                Employment(
                    id = "job-hours",
                    employerName = "Acme",
                    jobTitle = "Engineer",
                    startDate = date(2024, 1, 10),
                    endDate = null,
                    hoursPerWeek = null
                )
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(com.sidekick.opt_pal.core.calculations.UnemploymentDataQualityState.NEEDS_HOURS_REVIEW, state.dataQualityState)
        assertEquals("Review employment hours", state.unemploymentActionLabel)
        assertEquals("job-hours", state.firstEmploymentMissingHoursId)
    }

    @Test
    fun stemWithoutTrackingStartShowsMigrationCta() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val documentRepository = FakeDocumentRepository()
        val complianceHealthRepository = FakeComplianceHealthRepository()
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            caseStatusRepository,
            policyAlertRepository,
            documentRepository,
            complianceHealthRepository
        ) { date(2025, 3, 1) }

        val firebaseUser = mockUser("user-4")
        authRepository.emitUser(firebaseUser)
        authRepository.emitProfile(
            "user-4",
            UserProfile(
                uid = "user-4",
                email = "test@example.com",
                optType = "stem",
                optStartDate = date(2025, 1, 1),
                unemploymentTrackingStartDate = null
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(com.sidekick.opt_pal.core.calculations.UnemploymentDataQualityState.NEEDS_STEM_CYCLE_START, state.dataQualityState)
        assertEquals("Add original OPT start date", state.unemploymentActionLabel)
    }

    @Test
    fun disabledPolicyAlertFeedSuppressesDashboardHeadlineAndBadge() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val documentRepository = FakeDocumentRepository()
        val complianceHealthRepository = FakeComplianceHealthRepository()
        policyAlertRepository.availabilityResult = Result.success(
            PolicyAlertAvailability(
                isEnabled = false,
                message = "Limited rollout"
            )
        )
        policyAlertRepository.setAlerts(
            listOf(
                PolicyAlertCard(
                    id = "critical-alert",
                    title = "Critical travel change",
                    whatChanged = "Travel guidance changed.",
                    whoIsAffected = "OPT students.",
                    whyItMatters = "Trips may be affected.",
                    recommendedAction = "Review the source.",
                    source = PolicyAlertSource(
                        label = "DOS U.S. Visas News",
                        url = "https://travel.state.gov/content/travel/en/News/visas-news.html",
                        publishedAt = 1L
                    ),
                    severity = "critical",
                    confidence = "high",
                    finality = "guidance",
                    topics = listOf("travel"),
                    publishedAt = 2L
                )
            )
        )
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            caseStatusRepository,
            policyAlertRepository,
            documentRepository,
            complianceHealthRepository
        ) { date(2026, 3, 10) }

        authRepository.emitUser(mockUser("user-5"))
        authRepository.emitProfile(
            "user-5",
            UserProfile(uid = "user-5", email = "test@example.com", optType = "initial", optStartDate = date(2026, 1, 1))
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.policyAlertUnreadCount)
        assertEquals(null, state.latestCriticalPolicyAlertTitle)
    }

    @Test
    fun complianceScoreAppearsWhenFeatureEnabled() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val documentRepository = FakeDocumentRepository()
        val complianceHealthRepository = FakeComplianceHealthRepository()
        val now = date(2026, 3, 10)
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            caseStatusRepository,
            policyAlertRepository,
            documentRepository,
            complianceHealthRepository
        ) { now }

        authRepository.emitUser(mockUser("user-6"))
        authRepository.emitProfile(
            "user-6",
            UserProfile(
                uid = "user-6",
                email = "stable@example.com",
                optType = "initial",
                optStartDate = date(2026, 1, 1),
                unemploymentTrackingStartDate = date(2026, 1, 1),
                optEndDate = date(2026, 12, 31)
            )
        )
        dashboardRepository.setEmployments(
            listOf(
                Employment(
                    id = "job-1",
                    employerName = "Acme",
                    jobTitle = "Engineer",
                    startDate = date(2026, 1, 2),
                    endDate = null,
                    hoursPerWeek = 40
                )
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.complianceScore != null)
        assertTrue(complianceHealthRepository.syncRequests.isNotEmpty())
    }

    @Test
    fun latestScenarioOutcomeAppearsWhenDraftExists() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val documentRepository = FakeDocumentRepository()
        val complianceHealthRepository = FakeComplianceHealthRepository()
        val scenarioSimulatorRepository = FakeScenarioSimulatorRepository()
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            caseStatusRepository,
            policyAlertRepository,
            documentRepository,
            complianceHealthRepository,
            scenarioSimulatorRepository = scenarioSimulatorRepository
        ) { date(2026, 3, 10) }

        authRepository.emitUser(mockUser("user-7"))
        authRepository.emitProfile(
            "user-7",
            UserProfile(uid = "user-7", email = "scenario@example.com", optType = "initial", optStartDate = date(2026, 1, 1))
        )
        scenarioSimulatorRepository.setDrafts(
            "user-7",
            listOf(
                ScenarioDraft(
                    id = "draft-older",
                    name = "Older draft",
                    updatedAt = 100L,
                    lastOutcome = ScenarioDraftOutcomeSummary(
                        outcome = ScenarioOutcome.ACTION_REQUIRED.name,
                        headline = "Older scenario result",
                        computedAt = 100L
                    )
                ),
                ScenarioDraft(
                    id = "draft-latest",
                    name = "Cap-gap fallback",
                    updatedAt = 200L,
                    lastOutcome = ScenarioDraftOutcomeSummary(
                        outcome = ScenarioOutcome.HIGH_RISK.name,
                        headline = "Travel during pending COS can break cap-gap continuity.",
                        computedAt = 200L
                    )
                )
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Cap-gap fallback", state.latestScenarioDraftName)
        assertEquals("High risk", state.latestScenarioOutcomeLabel)
        assertEquals("Travel during pending COS can break cap-gap continuity.", state.latestScenarioHeadline)
    }

    @Test
    fun peerDataSummaryAppearsWhenSnapshotExists() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val documentRepository = FakeDocumentRepository()
        val complianceHealthRepository = FakeComplianceHealthRepository()
        val peerDataRepository = FakePeerDataRepository()
        peerDataRepository.cachedPeerDataSnapshot = PeerDataSnapshot(
            snapshotId = "cached",
            benchmarkCards = listOf(
                PeerBenchmarkCard(
                    id = "employment_timing",
                    title = "Employment timing",
                    summary = "About 68% were in qualifying work by day 30.",
                    source = "app_cohort",
                    cohortBasis = "Initial OPT • Computer and information sciences • 2026-H1",
                    sampleSizeBand = "50-99"
                )
            )
        )
        peerDataRepository.snapshotResult = Result.success(peerDataRepository.cachedPeerDataSnapshot!!)
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            caseStatusRepository,
            policyAlertRepository,
            documentRepository,
            complianceHealthRepository,
            peerDataRepository = peerDataRepository
        ) { date(2026, 3, 10) }

        authRepository.emitUser(mockUser("user-8"))
        authRepository.emitProfile(
            "user-8",
            UserProfile(
                uid = "user-8",
                email = "peer@example.com",
                optType = "initial",
                optStartDate = date(2026, 1, 1),
                peerDataEnabled = true
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Employment timing", state.peerDataTitle)
        assertEquals("About 68% were in qualifying work by day 30.", state.peerDataSummary)
        assertEquals("App cohort", state.peerDataSourceLabel)
        assertEquals("50-99", state.peerDataSampleSizeBand)
    }

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
