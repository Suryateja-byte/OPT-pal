package com.sidekick.opt_pal.core.session

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()

    @Test
    fun loggedOutStateIsExposed() = runTest {
        val viewModel = SessionViewModel(authRepository)
        authRepository.emitUser(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoggedIn)
        assertFalse(state.isCheckingAuth)
    }

    @Test
    fun incompleteProfileKeepsSetupFlow() = runTest {
        val viewModel = SessionViewModel(authRepository)
        val firebaseUser = mockUser("user-1")
        authRepository.emitUser(firebaseUser)
        authRepository.emitProfile(
            "user-1",
            UserProfile(uid = "user-1", email = "test@example.com", optType = null, optStartDate = null)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoggedIn)
        assertFalse(state.isProfileComplete)
    }

    @Test
    fun completeProfileMarksSetupDone() = runTest {
        val viewModel = SessionViewModel(authRepository)
        val firebaseUser = mockUser("user-2")
        authRepository.emitUser(firebaseUser)
        authRepository.emitProfile(
            "user-2",
            UserProfile(uid = "user-2", email = "test@example.com", optType = "stem", optStartDate = 10L)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoggedIn)
        assertTrue(state.isProfileComplete)
    }

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
