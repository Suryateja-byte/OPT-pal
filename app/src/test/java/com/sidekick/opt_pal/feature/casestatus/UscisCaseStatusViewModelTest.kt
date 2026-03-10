package com.sidekick.opt_pal.feature.casestatus

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.UscisCaseRefreshResult
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakeCaseStatusRepository
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UscisCaseStatusViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun invalidReceiptNumberShowsError() = runTest {
        val authRepository = FakeAuthRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        val viewModel = UscisCaseStatusViewModel(
            authRepository = authRepository,
            caseStatusRepository = caseStatusRepository,
            selectedCaseIdArg = null
        )
        authRepository.emitUser(mockUser("user-1"))
        advanceUntilIdle()

        viewModel.onReceiptNumberChanged("bad")
        viewModel.addCase()

        assertEquals(
            "Receipt number must match ABC1234567890.",
            viewModel.uiState.value.errorMessage
        )
    }

    @Test
    fun refreshCooldownMessageIsShown() = runTest {
        val authRepository = FakeAuthRepository()
        val caseStatusRepository = FakeCaseStatusRepository()
        caseStatusRepository.refreshResult = Result.success(
            UscisCaseRefreshResult(
                refreshed = false,
                cooldownRemainingMinutes = 7,
                caseId = "MSC1234567890"
            )
        )
        val viewModel = UscisCaseStatusViewModel(
            authRepository = authRepository,
            caseStatusRepository = caseStatusRepository,
            selectedCaseIdArg = null
        )
        authRepository.emitUser(mockUser("user-1"))
        caseStatusRepository.setTrackedCases(
            listOf(
                UscisCaseTracker(
                    id = "MSC1234567890",
                    receiptNumber = "MSC1234567890",
                    officialStatusText = "Case Was Received"
                )
            )
        )
        advanceUntilIdle()

        viewModel.refreshSelectedCase()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.infoMessage.orEmpty().contains("7 minutes"))
        assertEquals(listOf("MSC1234567890"), caseStatusRepository.refreshedCaseIds)
    }

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
