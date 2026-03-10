package com.sidekick.opt_pal.feature.reporting

import com.sidekick.opt_pal.data.model.ReportableEventType
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingSource
import com.sidekick.opt_pal.testing.fakes.FakeReportingRepository
import com.sidekick.opt_pal.testing.fakes.FakeUserSessionProvider
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ManageReportingViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val reportingRepository = FakeReportingRepository()
    private val userProvider = FakeUserSessionProvider("user-1")

    @Test
    fun saveReminderCreatesManualObligation() = runTest {
        val viewModel = ManageReportingViewModel(reportingRepository, userProvider, obligationId = null) { 0L }
        viewModel.onDescriptionChange("Address change")
        viewModel.onTypeSelected(ReportableEventType.ADDRESS_CHANGE)
        viewModel.onDueDateSelected(1_000L)

        viewModel.saveReminder()
        advanceUntilIdle()

        val obligation = reportingRepository.addedObligations.single()
        assertEquals("Address change", obligation.description)
        assertEquals(ReportingSource.MANUAL.name, obligation.createdBy)
    }

    @Test
    fun editReminderLoadsAndUpdatesExisting() = runTest {
        val existing = ReportingObligation(
            id = "task-1",
            eventType = ReportableEventType.OTHER.name,
            description = "Report employer change",
            eventDate = 500L,
            dueDate = 1_000L,
            createdBy = ReportingSource.MANUAL.name
        )
        reportingRepository.setObligations(listOf(existing))

        val viewModel = ManageReportingViewModel(reportingRepository, userProvider, obligationId = existing.id) { 0L }
        advanceUntilIdle()

        assertEquals("Report employer change", viewModel.uiState.value.description)
        viewModel.onDescriptionChange("Updated reminder")
        viewModel.saveReminder()
        advanceUntilIdle()

        val updated = reportingRepository.updatedObligations.single()
        assertEquals(existing.id, updated.id)
        assertEquals("Updated reminder", updated.description)
    }
}
