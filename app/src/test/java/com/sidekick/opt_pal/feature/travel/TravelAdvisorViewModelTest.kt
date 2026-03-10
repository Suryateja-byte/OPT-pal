package com.sidekick.opt_pal.feature.travel

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.TravelEntitlementSource
import com.sidekick.opt_pal.data.model.TravelEntitlementState
import com.sidekick.opt_pal.data.model.TravelPolicyBundle
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakeDashboardRepository
import com.sidekick.opt_pal.testing.fakes.FakeDocumentRepository
import com.sidekick.opt_pal.testing.fakes.FakeTravelAdvisorRepository
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TravelAdvisorViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun userFlagEntitlementKeepsFeatureEnabled() = runTest {
        val authRepository = FakeAuthRepository()
        val travelRepository = FakeTravelAdvisorRepository().apply {
            refreshResult = Result.success(policyBundle())
            entitlementResult = Result.success(
                TravelEntitlementState(
                    isEnabled = true,
                    source = TravelEntitlementSource.USER_FLAG,
                    message = "Enabled on profile."
                )
            )
        }
        val viewModel = createViewModel(authRepository, travelRepository)

        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile("user-1", UserProfile(uid = "user-1", travelAdvisorEnabled = true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entitlement.isEnabled)
        assertEquals(TravelEntitlementSource.USER_FLAG, viewModel.uiState.value.entitlement.source)
    }

    @Test
    fun openBetaFallbackEnablesFeatureWhenProfileFlagMissing() = runTest {
        val authRepository = FakeAuthRepository()
        val travelRepository = FakeTravelAdvisorRepository().apply {
            refreshResult = Result.success(policyBundle())
            entitlementResult = Result.success(
                TravelEntitlementState(
                    isEnabled = true,
                    source = TravelEntitlementSource.OPEN_BETA,
                    message = "Enabled for open beta."
                )
            )
        }
        val viewModel = createViewModel(authRepository, travelRepository)

        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile("user-1", UserProfile(uid = "user-1"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.entitlement.isEnabled)
        assertEquals(TravelEntitlementSource.OPEN_BETA, viewModel.uiState.value.entitlement.source)
        assertEquals("test", viewModel.uiState.value.policyBundle?.version)
    }

    @Test
    fun lockedPreviewStateIsExposedWhenRepositoryDisablesFeature() = runTest {
        val authRepository = FakeAuthRepository()
        val travelRepository = FakeTravelAdvisorRepository().apply {
            refreshResult = Result.success(policyBundle())
            entitlementResult = Result.success(
                TravelEntitlementState(
                    isEnabled = false,
                    source = TravelEntitlementSource.LOCKED_PREVIEW,
                    message = "Limited rollout."
                )
            )
        }
        val viewModel = createViewModel(authRepository, travelRepository)

        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile("user-1", UserProfile(uid = "user-1"))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.entitlement.isEnabled)
        assertEquals(TravelEntitlementSource.LOCKED_PREVIEW, viewModel.uiState.value.entitlement.source)
    }

    private fun createViewModel(
        authRepository: FakeAuthRepository,
        travelRepository: FakeTravelAdvisorRepository
    ): TravelAdvisorViewModel {
        return TravelAdvisorViewModel(
            authRepository = authRepository,
            dashboardRepository = FakeDashboardRepository(),
            documentRepository = FakeDocumentRepository(),
            travelAdvisorRepository = travelRepository,
            timeProvider = { 0L }
        )
    }

    private fun policyBundle(): TravelPolicyBundle {
        return TravelPolicyBundle(
            version = "test",
            generatedAt = 0L,
            lastReviewedAt = 0L,
            staleAfterDays = 30
        )
    }

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
