package com.sidekick.opt_pal.feature.peer

import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.data.model.PeerBenchmarkCard
import com.sidekick.opt_pal.data.model.PeerDataBundle
import com.sidekick.opt_pal.data.model.PeerDataParticipationSettings
import com.sidekick.opt_pal.data.model.PeerDataSnapshot
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.testing.fakes.FakeAuthRepository
import com.sidekick.opt_pal.testing.fakes.FakePeerDataRepository
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

class PeerDataViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun date(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun loadsBundleSnapshotAndParticipationSettings() = runTest {
        val authRepository = FakeAuthRepository()
        val peerDataRepository = FakePeerDataRepository()
        peerDataRepository.cachedPeerDataBundle = PeerDataBundle(version = "cached")
        peerDataRepository.refreshResult = Result.success(PeerDataBundle(version = "live"))
        peerDataRepository.cachedPeerDataSnapshot = PeerDataSnapshot(
            snapshotId = "cached",
            benchmarkCards = listOf(
                PeerBenchmarkCard(
                    id = "employment_timing",
                    title = "Employment timing",
                    summary = "About 70% were in qualifying work by day 30."
                )
            )
        )
        peerDataRepository.snapshotResult = Result.success(peerDataRepository.cachedPeerDataSnapshot!!)
        peerDataRepository.setSettings(
            "user-1",
            PeerDataParticipationSettings(
                contributionEnabled = true,
                contributionVersion = "live",
                previewedAt = date(2026, 3, 10),
                updatedAt = date(2026, 3, 10)
            )
        )
        val viewModel = PeerDataViewModel(
            authRepository = authRepository,
            peerDataRepository = peerDataRepository
        ) { date(2026, 3, 10) }

        authRepository.emitUser(mockUser("user-1"))
        authRepository.emitProfile(
            "user-1",
            UserProfile(
                uid = "user-1",
                peerDataEnabled = true,
                optType = "initial",
                optStartDate = date(2026, 1, 1)
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.entitlement.isEnabled)
        assertEquals("live", state.bundle?.version)
        assertEquals("cached", state.snapshot?.snapshotId)
        assertTrue(state.settings.contributionEnabled)
    }

    @Test
    fun togglingContributionPersistsSettings() = runTest {
        val authRepository = FakeAuthRepository()
        val peerDataRepository = FakePeerDataRepository()
        peerDataRepository.cachedPeerDataBundle = PeerDataBundle(version = "live")
        peerDataRepository.refreshResult = Result.success(PeerDataBundle(version = "live"))
        peerDataRepository.snapshotResult = Result.success(PeerDataSnapshot(snapshotId = "snapshot-live"))
        val viewModel = PeerDataViewModel(
            authRepository = authRepository,
            peerDataRepository = peerDataRepository
        ) { date(2026, 3, 10) }

        authRepository.emitUser(mockUser("user-2"))
        authRepository.emitProfile(
            "user-2",
            UserProfile(
                uid = "user-2",
                peerDataEnabled = true,
                optType = "initial",
                optStartDate = date(2026, 1, 1)
            )
        )
        advanceUntilIdle()

        viewModel.setContributionEnabled(true)
        advanceUntilIdle()

        assertEquals(1, peerDataRepository.saveRequests.size)
        assertTrue(peerDataRepository.saveRequests.first().first)
        assertTrue(viewModel.uiState.value.settings.contributionEnabled)
    }

    private fun mockUser(uid: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        return user
    }
}
