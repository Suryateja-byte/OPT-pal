package com.sidekick.opt_pal.feature.h1b

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.H1bDashboardBundle
import com.sidekick.opt_pal.data.model.H1bWorkflowStage
import com.sidekick.opt_pal.data.model.UscisTrackedFormType
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.model.VisaPathwayEmployerType
import com.sidekick.opt_pal.data.model.VisaPathwayProfile
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakeCaseStatusRepository
import com.sidekick.opt_pal.testing.fakes.FakeDashboardRepository
import com.sidekick.opt_pal.testing.fakes.FakeH1bDashboardRepository
import com.sidekick.opt_pal.testing.fakes.FakeVisaPathwayPlannerRepository
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class H1bDashboardViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun plannerAndEmploymentPrefillDashboardProfile() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val plannerRepository = FakeVisaPathwayPlannerRepository()
        val h1bDashboardRepository = FakeH1bDashboardRepository()
        h1bDashboardRepository.cachedDashboardBundle = H1bDashboardBundle(version = "cached")
        h1bDashboardRepository.refreshResult = Result.success(H1bDashboardBundle(version = "live"))
        val viewModel = H1bDashboardViewModel(
            authRepository = authRepository,
            dashboardRepository = dashboardRepository,
            caseStatusRepository = caseStatusRepository,
            plannerRepository = plannerRepository,
            h1bDashboardRepository = h1bDashboardRepository
        )

        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile(
            "user-1",
            UserProfile(uid = "user-1", email = "sam@example.com", optEndDate = date(2026, 6, 1))
        )
        dashboardRepository.setEmployments(
            listOf(
                Employment(
                    id = "job-1",
                    employerName = "Acme Labs",
                    jobTitle = "Engineer",
                    startDate = date(2026, 1, 5),
                    endDate = null,
                    hoursPerWeek = 40
                )
            )
        )
        plannerRepository.setProfile(
            "user-1",
            VisaPathwayProfile(
                employerType = VisaPathwayEmployerType.PRIVATE_COMPANY.wireValue,
                employerWillSponsorH1b = true,
                roleDirectlyRelatedToDegree = true
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Acme Labs", state.h1bProfile.employerName)
        assertEquals(VisaPathwayEmployerType.PRIVATE_COMPANY, state.h1bProfile.parsedEmployerType)
        assertEquals(true, state.h1bProfile.selfReportedSponsorIntent)
        assertEquals(true, state.h1bProfile.roleMatchesSpecialtyOccupation)
    }

    @Test
    fun trackingI129LinksCaseAndUsesI129Hint() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val plannerRepository = FakeVisaPathwayPlannerRepository()
        val h1bDashboardRepository = FakeH1bDashboardRepository()
        h1bDashboardRepository.refreshResult = Result.success(
            H1bDashboardBundle(
                version = "live",
                capSeason = com.sidekick.opt_pal.data.model.H1bCapSeason(fiscalYear = 2027)
            )
        )
        val viewModel = H1bDashboardViewModel(
            authRepository = authRepository,
            dashboardRepository = dashboardRepository,
            caseStatusRepository = caseStatusRepository,
            plannerRepository = plannerRepository,
            h1bDashboardRepository = h1bDashboardRepository
        )

        authRepository.emitUser(mockUser("user-2"))
        authRepository.emitProfile("user-2", UserProfile(uid = "user-2", optEndDate = date(2026, 6, 1)))
        advanceUntilIdle()

        viewModel.onReceiptNumberChanged("wac1234567890")
        viewModel.trackI129Case()
        advanceUntilIdle()

        assertEquals(listOf("WAC1234567890"), caseStatusRepository.trackedReceipts)
        assertEquals(listOf(UscisTrackedFormType.I129), caseStatusRepository.trackedFormHints)
        assertTrue(h1bDashboardRepository.savedCaseTrackingStates.isNotEmpty())
        assertEquals(
            H1bWorkflowStage.NOT_STARTED.wireValue,
            h1bDashboardRepository.savedTimelineStates.last().second.workflowStage
        )
        assertTrue(h1bDashboardRepository.savedTimelineStates.last().second.receiptReceivedAt != null)
    }

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
