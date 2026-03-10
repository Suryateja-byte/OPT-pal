package com.sidekick.opt_pal.core.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecuritySessionManagerTest {

    @Test
    fun authenticatedSessionStartsUnlockedWhenCredentialAvailable() {
        val manager = createManager(
            biometricStatus = SecurityManager.BiometricStatus.AVAILABLE
        )

        manager.onAuthStateChanged(isLoggedIn = true)

        assertTrue(manager.state.value.isAuthenticatedSession)
        assertFalse(manager.state.value.isLocked)
        manager.close()
    }

    @Test
    fun authenticatedSessionRequiresSetupWithoutCredential() {
        val manager = createManager(
            biometricStatus = SecurityManager.BiometricStatus.NONE_ENROLLED
        )

        manager.onAuthStateChanged(isLoggedIn = true)

        assertTrue(manager.state.value.isLocked)
        assertTrue(manager.state.value.requiresSecuritySetup)
        manager.close()
    }

    private fun createManager(
        biometricStatus: SecurityManager.BiometricStatus
    ): CloseableSecuritySessionManager {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return CloseableSecuritySessionManager(
            SecuritySessionManager(
                biometricStatusProvider = { biometricStatus },
                timeoutMs = 60_000L,
                scope = scope
            ),
            scope
        )
    }

    private class CloseableSecuritySessionManager(
        private val delegate: SecuritySessionManager,
        private val scope: CoroutineScope
    ) {
        val state = delegate.state

        fun onAuthStateChanged(isLoggedIn: Boolean) = delegate.onAuthStateChanged(isLoggedIn)

        fun close() {
            scope.cancel()
        }
    }
}
