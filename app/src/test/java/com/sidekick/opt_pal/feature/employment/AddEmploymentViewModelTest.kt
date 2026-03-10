package com.sidekick.opt_pal.feature.employment

import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.testing.fakes.FakeDashboardRepository
import com.sidekick.opt_pal.testing.fakes.FakeReportingRepository
import com.sidekick.opt_pal.testing.fakes.FakeUserSessionProvider
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddEmploymentViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val dashboardRepository = FakeDashboardRepository()
    private val reportingRepository = FakeReportingRepository()
    private val userSessionProvider = FakeUserSessionProvider("user-1")

    @Test
    fun saveEmploymentRequiresLoggedInUser() = runTest {
        val viewModel = AddEmploymentViewModel(dashboardRepository, reportingRepository, userSessionProvider)
        userSessionProvider.userId = null

        viewModel.saveEmployment()

        assertEquals("User not logged in.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun saveEmploymentValidatesFields() = runTest {
        val viewModel = AddEmploymentViewModel(dashboardRepository, reportingRepository, userSessionProvider)
        userSessionProvider.userId = "user-1"

        viewModel.saveEmployment()

        assertEquals("Please fill in all fields.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun saveEmploymentPersistsData() = runTest {
        val viewModel = AddEmploymentViewModel(dashboardRepository, reportingRepository, userSessionProvider)
        viewModel.onEmployerNameChange("Acme")
        viewModel.onJobTitleChange("Engineer")
        val startDate = 1000L
        viewModel.onStartDateSelected(startDate)
        viewModel.onIsCurrentJobChange(false)
        viewModel.onEndDateSelected(2000L)

        viewModel.saveEmployment()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.onSaveComplete)
        val savedEmployment = dashboardRepository.addedEmployments.single()
        assertEquals("Acme", savedEmployment.employerName)
        assertEquals(startDate, savedEmployment.startDate)
        assertEquals(1, reportingRepository.addedObligations.size)
        val obligation = reportingRepository.addedObligations.first()
        assertEquals(startDate + 10 * 86_400_000L, obligation.dueDate)
    }

    @Test
    fun editEmploymentUpdatesExistingEntryWithoutNewReportingTask() = runTest {
        val existing = Employment(
            id = "job-1",
            employerName = "Acme",
            jobTitle = "Engineer",
            startDate = 1_000L,
            endDate = null
        )
        dashboardRepository.setEmployments(listOf(existing))

        val viewModel = AddEmploymentViewModel(
            dashboardRepository,
            reportingRepository,
            userSessionProvider,
            employmentId = existing.id
        )

        advanceUntilIdle()
        assertEquals("Acme", viewModel.uiState.value.employerName)

        viewModel.onJobTitleChange("Senior Engineer")
        viewModel.onIsCurrentJobChange(false)
        viewModel.onEndDateSelected(2_000L)

        viewModel.saveEmployment()
        advanceUntilIdle()

        val savedEmployment = dashboardRepository.addedEmployments.last()
        assertEquals(existing.id, savedEmployment.id)
        assertEquals("Senior Engineer", savedEmployment.jobTitle)
        assertTrue("No extra reporting tasks", reportingRepository.addedObligations.isEmpty())
    }
}
