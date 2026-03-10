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
    fun saveEmploymentRequiresValidHours() = runTest {
        val viewModel = AddEmploymentViewModel(dashboardRepository, reportingRepository, userSessionProvider)
        viewModel.onEmployerNameChange("Acme")
        viewModel.onJobTitleChange("Engineer")
        viewModel.onStartDateSelected(1_000L)
        viewModel.onHoursPerWeekChange("0")

        viewModel.saveEmployment()

        assertEquals("Enter valid hours per week (1-168).", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun saveEmploymentPersistsData() = runTest {
        val viewModel = AddEmploymentViewModel(dashboardRepository, reportingRepository, userSessionProvider)
        viewModel.onEmployerNameChange("Acme")
        viewModel.onJobTitleChange("Engineer")
        viewModel.onHoursPerWeekChange("40")
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
        assertEquals(40, savedEmployment.hoursPerWeek)
        assertEquals(1, reportingRepository.startedWizards.size)
        assertEquals("wizard-1", viewModel.uiState.value.createdWizardId)
    }

    @Test
    fun editEmploymentEndingCurrentJobCreatesWizard() = runTest {
        val existing = Employment(
            id = "job-1",
            employerName = "Acme",
            jobTitle = "Engineer",
            startDate = 1_000L,
            hoursPerWeek = 20,
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
        viewModel.onHoursPerWeekChange("25")
        viewModel.onIsCurrentJobChange(false)
        viewModel.onEndDateSelected(2_000L)

        viewModel.saveEmployment()
        advanceUntilIdle()

        val savedEmployment = dashboardRepository.addedEmployments.last()
        assertEquals(existing.id, savedEmployment.id)
        assertEquals("Senior Engineer", savedEmployment.jobTitle)
        assertEquals(25, savedEmployment.hoursPerWeek)
        assertEquals(1, reportingRepository.startedWizards.size)
        assertEquals("wizard-1", viewModel.uiState.value.createdWizardId)
    }

    @Test
    fun editEmploymentWithoutEndingDoesNotCreateWizard() = runTest {
        val existing = Employment(
            id = "job-2",
            employerName = "Beta",
            jobTitle = "Analyst",
            startDate = 2_000L,
            hoursPerWeek = 40,
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
        viewModel.onJobTitleChange("Senior Analyst")
        viewModel.onHoursPerWeekChange("40")
        viewModel.saveEmployment()
        advanceUntilIdle()

        assertTrue(reportingRepository.startedWizards.isEmpty())
    }
}
