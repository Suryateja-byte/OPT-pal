package com.sidekick.opt_pal.core.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L
private const val LOCK_CHECK_INTERVAL_MS = 15_000L

data class SecuritySessionState(
    val isLocked: Boolean = false,
    val lastUnlockAt: Long? = null,
    val lastActivityAt: Long? = null,
    val hasSecureCredential: Boolean = false,
    val requiresSecuritySetup: Boolean = false,
    val isAuthenticatedSession: Boolean = false,
    val unlockError: String? = null
)

class SecuritySessionManager(
    private val biometricStatusProvider: () -> SecurityManager.BiometricStatus,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    private val _state = MutableStateFlow(SecuritySessionState())
    val state: StateFlow<SecuritySessionState> = _state.asStateFlow()

    init {
        scope.launch {
            while (true) {
                delay(LOCK_CHECK_INTERVAL_MS)
                if (shouldLockForInactivity()) {
                    lock("Session timed out.")
                }
            }
        }
    }

    fun onAuthStateChanged(isLoggedIn: Boolean) {
        if (!isLoggedIn) {
            _state.value = SecuritySessionState()
            return
        }

        refreshCredentialState()
        if (_state.value.hasSecureCredential) {
            unlockFromPasswordAuth()
        } else {
            _state.update {
                it.copy(
                    isAuthenticatedSession = true,
                    isLocked = true,
                    requiresSecuritySetup = true,
                    unlockError = null
                )
            }
        }
    }

    fun onForegrounded() {
        if (!_state.value.isAuthenticatedSession) return
        refreshCredentialState()
        if (!_state.value.hasSecureCredential) {
            _state.update { it.copy(isLocked = true, requiresSecuritySetup = true) }
            return
        }
        if (shouldLockForInactivity()) {
            lock("Session timed out.")
        }
    }

    fun onBackgrounded() {
        if (_state.value.isAuthenticatedSession && shouldLockForInactivity()) {
            lock("Session timed out.")
        }
    }

    fun recordUserInteraction() {
        val current = _state.value
        if (!current.isAuthenticatedSession || current.isLocked) return
        _state.update { it.copy(lastActivityAt = clock()) }
    }

    fun unlockFromPasswordAuth() {
        val now = clock()
        _state.update {
            it.copy(
                isAuthenticatedSession = true,
                isLocked = false,
                requiresSecuritySetup = !it.hasSecureCredential,
                lastUnlockAt = now,
                lastActivityAt = now,
                unlockError = null
            )
        }
    }

    fun onUnlockSucceeded() {
        val now = clock()
        _state.update {
            it.copy(
                isLocked = false,
                requiresSecuritySetup = false,
                hasSecureCredential = true,
                lastUnlockAt = now,
                lastActivityAt = now,
                unlockError = null
            )
        }
    }

    fun onUnlockError(message: String?) {
        _state.update {
            it.copy(
                isLocked = true,
                unlockError = message?.takeIf(String::isNotBlank)
            )
        }
    }

    fun clearUnlockError() {
        _state.update { it.copy(unlockError = null) }
    }

    fun refreshCredentialState() {
        val hasCredential = biometricStatusProvider() == SecurityManager.BiometricStatus.AVAILABLE
        _state.update {
            it.copy(
                hasSecureCredential = hasCredential,
                requiresSecuritySetup = it.isAuthenticatedSession && !hasCredential
            )
        }
    }

    private fun shouldLockForInactivity(): Boolean {
        val current = _state.value
        if (!current.isAuthenticatedSession || current.isLocked) return false
        val referenceTime = current.lastActivityAt ?: current.lastUnlockAt ?: return false
        return clock() - referenceTime >= timeoutMs
    }

    private fun lock(message: String? = null) {
        _state.update {
            it.copy(
                isLocked = true,
                unlockError = message
            )
        }
    }

    constructor(
        securityManager: SecurityManager,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        clock: () -> Long = System::currentTimeMillis,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    ) : this(
        biometricStatusProvider = securityManager::isBiometricAvailable,
        timeoutMs = timeoutMs,
        clock = clock,
        scope = scope
    )
}
