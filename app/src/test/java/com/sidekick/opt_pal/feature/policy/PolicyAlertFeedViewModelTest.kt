package com.sidekick.opt_pal.feature.policy

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.PolicyAlertAvailability
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertSource
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakePolicyAlertRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class PolicyAlertFeedViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun selectsInitialAlertAndMarksItOpened() = runTest {
        val authRepository = FakeAuthRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val viewModel = PolicyAlertFeedViewModel(
            authRepository = authRepository,
            policyAlertRepository = policyAlertRepository,
            selectedAlertIdArg = "alert-2",
            initialFilterArg = null
        )
        policyAlertRepository.availabilityResult = Result.success(
            PolicyAlertAvailability(isEnabled = true, message = "Enabled")
        )
        policyAlertRepository.setAlerts(
            listOf(
                alert(id = "alert-1", title = "Travel guidance", topics = listOf("travel")),
                alert(id = "alert-2", title = "Reporting guidance", topics = listOf("reporting"))
            )
        )
        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile("user-1", UserProfile(uid = "user-1", email = "test@example.com", optType = "initial"))

        advanceUntilIdle()

        assertEquals("alert-2", viewModel.uiState.value.selectedAlert?.id)
        assertTrue(policyAlertRepository.openedAlertIds.contains("user-1" to "alert-2"))
    }

    @Test
    fun stemAudienceFiltersOutInitialOnlyAlerts() = runTest {
        val authRepository = FakeAuthRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        val viewModel = PolicyAlertFeedViewModel(
            authRepository = authRepository,
            policyAlertRepository = policyAlertRepository,
            selectedAlertIdArg = null,
            initialFilterArg = null
        )
        policyAlertRepository.setAlerts(
            listOf(
                alert(id = "initial-alert", audience = "initial_opt"),
                alert(id = "stem-alert", audience = "stem_opt")
            )
        )
        authRepository.emitUser(mockUser("user-2"))
        authRepository.emitProfile("user-2", UserProfile(uid = "user-2", email = "stem@example.com", optType = "stem"))

        advanceUntilIdle()

        assertEquals(listOf("stem-alert"), viewModel.uiState.value.visibleAlerts.map { it.id })
    }

    @Test
    fun disabledAvailabilityHidesFeedContent() = runTest {
        val authRepository = FakeAuthRepository()
        val policyAlertRepository = FakePolicyAlertRepository()
        policyAlertRepository.availabilityResult = Result.success(
            PolicyAlertAvailability(
                isEnabled = false,
                message = "Limited rollout"
            )
        )
        policyAlertRepository.setAlerts(listOf(alert(id = "alert-1")))
        val viewModel = PolicyAlertFeedViewModel(
            authRepository = authRepository,
            policyAlertRepository = policyAlertRepository,
            selectedAlertIdArg = "alert-1",
            initialFilterArg = null
        )

        authRepository.emitUser(mockUser("user-3"))
        authRepository.emitProfile("user-3", UserProfile(uid = "user-3", email = "test@example.com", optType = "initial"))

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.visibleAlerts.isEmpty())
        assertEquals(null, viewModel.uiState.value.selectedAlert)
        assertTrue(policyAlertRepository.openedAlertIds.isEmpty())
    }

    private fun alert(
        id: String,
        title: String = "Alert",
        audience: String = "all_opt_users",
        topics: List<String> = listOf("applications")
    ) = PolicyAlertCard(
        id = id,
        title = title,
        whatChanged = "Updated policy.",
        whoIsAffected = "Students on OPT.",
        whyItMatters = "It matters for status planning.",
        recommendedAction = "Review the source.",
        source = PolicyAlertSource(
            label = "USCIS Alerts",
            url = "https://www.uscis.gov/alerts/example",
            publishedAt = 1L
        ),
        severity = "critical",
        confidence = "high",
        finality = "guidance",
        topics = topics,
        audience = audience,
        publishedAt = 1L
    )

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
