package com.sidekick.opt_pal.feature.dashboard

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakeDashboardRepository
import com.sidekick.opt_pal.testing.fakes.FakeDocumentRepository
import com.sidekick.opt_pal.testing.fakes.FakeReportingRepository
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
        val documentRepository = FakeDocumentRepository()
        val now = date(2024, 1, 20)
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            documentRepository
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
                    endDate = null
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
        assertEquals(1, state.pendingReportingCount)
        assertEquals(date(2024, 2, 1), state.nextReportingDue)
    }

    @Test
    fun deleteEmploymentPassesIdToRepository() = runTest {
        val authRepository = FakeAuthRepository()
        val dashboardRepository = FakeDashboardRepository()
        val reportingRepository = FakeReportingRepository()
        val documentRepository = FakeDocumentRepository()
        val viewModel = DashboardViewModel(
            authRepository,
            dashboardRepository,
            reportingRepository,
            documentRepository
        )
        val firebaseUser = mockUser("user-2")
        authRepository.emitUser(firebaseUser)
        advanceUntilIdle()

        viewModel.deleteEmployment("job-22")
        advanceUntilIdle()

        assertTrue(dashboardRepository.deletedEmploymentIds.contains("job-22"))
    }

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
