package com.sidekick.opt_pal.feature.reporting

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.ReportingDraftConfidence
import com.sidekick.opt_pal.data.model.ReportingDraftResult
import com.sidekick.opt_pal.data.model.ReportingWizard
import com.sidekick.opt_pal.data.model.ReportingWizardEventType
import com.sidekick.opt_pal.data.model.ReportingWizardOptRegime
import com.sidekick.opt_pal.data.model.ReportingWizardStatus
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

class ReportingWizardViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val dashboardRepository = FakeDashboardRepository()
    private val documentRepository = FakeDocumentRepository()
    private val reportingRepository = FakeReportingRepository()

    @Test
    fun manualStartCreatesWizardFromSelectedEmployment() = runTest {
        val user = fakeUser("user-1")
        authRepository.emitUser(user)
        authRepository.emitProfile("user-1", UserProfile(uid = "user-1", optType = "initial", majorName = "Computer Science"))
        dashboardRepository.setEmployments(
            listOf(
                Employment(
                    id = "job-1",
                    employerName = "Acme",
                    jobTitle = "Engineer",
                    startDate = 1_000L,
                    hoursPerWeek = 40
                )
            )
        )

        val viewModel = ReportingWizardViewModel(
            authRepository = authRepository,
            dashboardRepository = dashboardRepository,
            documentRepository = documentRepository,
            reportingRepository = reportingRepository,
            wizardIdArg = null,
            obligationIdArg = null
        )
        advanceUntilIdle()

        viewModel.onEmploymentSelected("job-1")
        viewModel.onEventTypeSelected(ReportingWizardEventType.NEW_EMPLOYER)
        viewModel.startWizard()
        advanceUntilIdle()

        assertEquals("wizard-1", viewModel.uiState.value.wizardId)
        assertEquals(ReportingWizardStep.DETAILS, viewModel.uiState.value.step)
        assertEquals(1, reportingRepository.startedWizards.size)
    }

    @Test
    fun generateDraftUsesSavedInputsAndTransitionsToReview() = runTest {
        val user = fakeUser("user-1")
        authRepository.emitUser(user)
        authRepository.emitProfile("user-1", UserProfile(uid = "user-1", optType = "stem"))
        dashboardRepository.setEmployments(
            listOf(
                Employment(
                    id = "job-2",
                    employerName = "Beta",
                    jobTitle = "Analyst",
                    startDate = 2_000L,
                    hoursPerWeek = 20
                )
            )
        )
        reportingRepository.setWizards(
            listOf(
                ReportingWizard(
                    id = "wizard-1",
                    eventType = ReportingWizardEventType.MATERIAL_CHANGE.wireValue,
                    optRegime = ReportingWizardOptRegime.STEM.wireValue,
                    status = ReportingWizardStatus.READY.wireValue,
                    eventDate = 2_500L,
                    dueDate = 3_500L,
                    obligationId = "obligation-1",
                    relatedEmploymentId = "job-2"
                )
            )
        )
        reportingRepository.generatedDraftResult = Result.success(
            ReportingDraftResult(
                confidence = ReportingDraftConfidence.HIGH.wireValue,
                draftParagraph = "My work applies data analysis methods from my Computer Science coursework."
            )
        )

        val viewModel = ReportingWizardViewModel(
            authRepository = authRepository,
            dashboardRepository = dashboardRepository,
            documentRepository = documentRepository,
            reportingRepository = reportingRepository,
            wizardIdArg = "wizard-1",
            obligationIdArg = null
        )
        advanceUntilIdle()

        viewModel.onMajorNameChanged("Computer Science")
        viewModel.onJobDutiesChanged("Analyze production metrics and write SQL dashboards.")
        viewModel.continueToReview()
        advanceUntilIdle()
        viewModel.generateDraft()
        advanceUntilIdle()

        assertEquals(ReportingWizardStep.REVIEW, viewModel.uiState.value.step)
        assertTrue(reportingRepository.updatedWizardInputs.isNotEmpty())
        assertEquals(
            "My work applies data analysis methods from my Computer Science coursework.",
            viewModel.uiState.value.editedDraft
        )
    }

    private fun fakeUser(uid: String): FirebaseUser {
        return mockk {
            every { this@mockk.uid } returns uid
        }
    }
}
