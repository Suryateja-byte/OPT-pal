package com.sidekick.opt_pal.feature.pathway

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.I983Draft
import com.sidekick.opt_pal.data.model.I983DraftStatus
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayPlannerBundle
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import com.sidekick.opt_pal.data.model.VisaPathwayH1bSeason
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakeCaseStatusRepository
import com.sidekick.opt_pal.testing.fakes.FakeDashboardRepository
import com.sidekick.opt_pal.testing.fakes.FakeDocumentRepository
import com.sidekick.opt_pal.testing.fakes.FakeI983AssistantRepository
import com.sidekick.opt_pal.testing.fakes.FakePolicyAlertRepository
import com.sidekick.opt_pal.testing.fakes.FakeReportingRepository
import com.sidekick.opt_pal.testing.fakes.FakeVisaPathwayPlannerRepository
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class VisaPathwayPlannerViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun buildsTopRecommendationFromObservedData() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val documentRepository = FakeDocumentRepository()
        val i983Repository = FakeI983AssistantRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val plannerRepository = FakeVisaPathwayPlannerRepository()
        plannerRepository.cachedPlannerBundle = bundle()
        plannerRepository.refreshResult = Result.success(bundle())

        val viewModel = VisaPathwayPlannerViewModel(
            authRepository = authRepository,
            dashboardRepository = dashboardRepository,
            reportingRepository = reportingRepository,
            documentRepository = documentRepository,
            i983AssistantRepository = i983Repository,
            caseStatusRepository = caseStatusRepository,
            policyAlertRepository = policyAlertRepository,
            plannerRepository = plannerRepository,
            initialPathwayId = null,
            timeProvider = { date(2026, 3, 10) }
        )

        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile(
            "user-1",
            UserProfile(
                uid = "user-1",
                email = "test@example.com",
                visaPathwayPlannerEnabled = true,
                optType = "initial",
                optEndDate = date(2026, 7, 1),
                cipCode = "11.0701"
            )
        )
        plannerRepository.setProfile(
            "user-1",
            VisaPathwayProfile(
                employerUsesEVerify = true,
                roleDirectlyRelatedToDegree = true
            )
        )
        dashboardRepository.setEmployments(
            listOf(
                Employment(
                    id = "employment-1",
                    employerName = "Acme",
                    jobTitle = "Engineer",
                    startDate = date(2026, 1, 5),
                    endDate = null,
                    hoursPerWeek = 40
                )
            )
        )
        i983Repository.setDrafts(
            "user-1",
            listOf(
                I983Draft(
                    id = "draft-1",
                    linkedEmploymentId = "employment-1",
                    status = I983DraftStatus.READY_TO_EXPORT.wireValue
                )
            )
        )

        advanceUntilIdle()

        assertEquals("STEM OPT", viewModel.uiState.value.summary.topAssessment?.title)
        assertTrue(viewModel.uiState.value.temporaryAssessments.isNotEmpty())
    }

    @Test
    fun markPreferredPathwayPersistsProfile() = runTest {
        val authRepository = FakeAuthRepository()
        val plannerRepository = FakeVisaPathwayPlannerRepository()
        plannerRepository.cachedPlannerBundle = bundle()
        plannerRepository.refreshResult = Result.success(bundle())
        val viewModel = VisaPathwayPlannerViewModel(
            authRepository = authRepository,
            dashboardRepository = FakeDashboardRepository(),
            reportingRepository = FakeReportingRepository(),
            documentRepository = FakeDocumentRepository(),
            i983AssistantRepository = FakeI983AssistantRepository(),
            caseStatusRepository = FakeCaseStatusRepository(),
            policyAlertRepository = FakePolicyAlertRepository(),
            plannerRepository = plannerRepository,
            initialPathwayId = null,
            timeProvider = { date(2026, 3, 10) }
        )

        authRepository.emitUser(mockUser("user-2"))
        authRepository.emitProfile("user-2", UserProfile(uid = "user-2", email = "test@example.com", visaPathwayPlannerEnabled = true))

        advanceUntilIdle()

        viewModel.markPreferredPathway(com.sidekick.opt_pal.data.model.VisaPathwayId.H1B_CAP_SUBJECT)
        advanceUntilIdle()

        assertEquals("h1b_cap_subject", plannerRepository.savedProfiles.last().second.preferredPathwayId)
    }

    private fun bundle(): VisaPathwayPlannerBundle = VisaPathwayPlannerBundle(
        version = "test",
        generatedAt = 0L,
        lastReviewedAt = date(2026, 3, 10),
        stemEligibleCipPrefixes = listOf("11."),
        h1bSeason = VisaPathwayH1bSeason(fiscalYear = 2027, isPublished = false)
    )

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
