package com.sidekick.opt_pal.feature.reporting

import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.ReportingActionType
import com.sidekick.opt_pal.data.model.ReportingSource
import com.sidekick.opt_pal.testing.fakes.FakeReportingRepository
import com.sidekick.opt_pal.testing.fakes.FakeUserSessionProvider
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReportingViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val reportingRepository = FakeReportingRepository()
    private val userProvider = FakeUserSessionProvider("user-1")

    @Test
    fun uiStateSplitsPendingAndCompleted() = runTest {
        reportingRepository.setObligations(
            listOf(
                ReportingObligation(
                    id = "pending",
                    isCompleted = false,
                    createdBy = ReportingSource.AUTO.name,
                    actionType = ReportingActionType.OPEN_WIZARD.wireValue
                ),
                ReportingObligation(id = "manual", isCompleted = false),
                ReportingObligation(id = "done", isCompleted = true)
            )
        )
        val viewModel = ReportingViewModel(reportingRepository, userProvider)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.actionItems.size)
        assertEquals("pending", state.actionItems.first().obligation.id)
        assertEquals(1, state.pendingObligations.size)
        assertEquals("manual", state.pendingObligations.first().id)
        assertEquals(1, state.completedObligations.size)
        assertEquals("done", state.completedObligations.first().id)
    }

    @Test
    fun toggleCompletionInvokesRepository() = runTest {
        val viewModel = ReportingViewModel(reportingRepository, userProvider)
        val obligation = ReportingObligation(id = "pending", isCompleted = false)

        viewModel.toggleCompletion(obligation)
        advanceUntilIdle()

        assertEquals(
            Triple("user-1", "pending", true),
            reportingRepository.toggledObligations.single()
        )
    }
}
