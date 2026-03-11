package com.sidekick.opt_pal.feature.peer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.data.model.PeerDataBundle
import com.sidekick.opt_pal.data.model.PeerDataEntitlementState
import com.sidekick.opt_pal.data.model.PeerDataParticipationSettings
import com.sidekick.opt_pal.data.model.PeerDataSnapshot
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.PeerDataRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class PeerDataUiState(
    val isLoading: Boolean = true,
    val isRefreshingBundle: Boolean = false,
    val isRefreshingSnapshot: Boolean = false,
    val isSavingParticipation: Boolean = false,
    val entitlement: PeerDataEntitlementState = PeerDataEntitlementState(),
    val bundle: PeerDataBundle? = null,
    val settings: PeerDataParticipationSettings = PeerDataParticipationSettings(),
    val snapshot: PeerDataSnapshot? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class PeerDataViewModel(
    private val authRepository: AuthRepository,
    private val peerDataRepository: PeerDataRepository,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeerDataUiState())
    val uiState = _uiState.asStateFlow()

    private val entitlementState = MutableStateFlow(PeerDataEntitlementState())
    private val bundleState = MutableStateFlow<PeerDataBundle?>(peerDataRepository.getCachedBundle())
    private val snapshotState = MutableStateFlow<PeerDataSnapshot?>(peerDataRepository.getCachedSnapshot())

    private var currentUid: String? = null
    private var observationJob: Job? = null
    private var lastEntitlementFlag: Boolean? = null

    init {
        authRepository.getAuthState()
            .onEach { user ->
                currentUid = user?.uid
                lastEntitlementFlag = null
                observationJob?.cancel()
                if (user == null) {
                    _uiState.value = PeerDataUiState(
                        isLoading = false,
                        errorMessage = "User not logged in."
                    )
                } else {
                    loadBundle()
                    observeUserData(user.uid)
                }
            }
            .launchIn(viewModelScope)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    fun refreshAll() {
        loadBundle(forceInfoMessage = true)
        refreshSnapshot(forceInfoMessage = true)
    }

    fun setContributionEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSavingParticipation = true,
            errorMessage = null,
            infoMessage = null
        )
        viewModelScope.launch {
            peerDataRepository.saveParticipationSettings(
                contributionEnabled = enabled,
                previewedAt = timeProvider()
            ).onSuccess { settings ->
                _uiState.value = _uiState.value.copy(
                    isSavingParticipation = false,
                    settings = settings,
                    infoMessage = if (enabled) {
                        "Peer benchmark contribution enabled."
                    } else {
                        "Peer benchmark contribution disabled."
                    }
                )
                refreshSnapshot()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSavingParticipation = false,
                    errorMessage = error.message ?: "Unable to update Peer Data participation."
                )
            }
        }
    }

    private fun observeUserData(uid: String) {
        observationJob = combine(
            authRepository.getUserProfile(uid),
            peerDataRepository.observeParticipationSettings(uid),
            entitlementState,
            bundleState,
            snapshotState
        ) { profile, settings, entitlement, bundle, snapshot ->
            PeerObservedData(
                userFlag = profile?.peerDataEnabled,
                settings = settings,
                entitlement = entitlement,
                bundle = bundle,
                snapshot = snapshot
            )
        }.onEach { observed ->
            maybeResolveEntitlement(observed.userFlag)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                entitlement = observed.entitlement,
                bundle = observed.bundle,
                settings = observed.settings,
                snapshot = observed.snapshot
            )
        }.launchIn(viewModelScope)
    }

    private fun maybeResolveEntitlement(userFlag: Boolean?) {
        if (userFlag == lastEntitlementFlag) return
        lastEntitlementFlag = userFlag
        viewModelScope.launch {
            peerDataRepository.resolveEntitlement(userFlag)
                .onSuccess { entitlement ->
                    entitlementState.value = entitlement
                    if (entitlement.isEnabled) {
                        refreshSnapshot()
                    }
                }
                .onFailure { error ->
                    entitlementState.value = PeerDataEntitlementState(
                        isEnabled = false,
                        message = error.message ?: "Unable to resolve Peer Data access."
                    )
                }
        }
    }

    private fun loadBundle(forceInfoMessage: Boolean = false) {
        val cached = peerDataRepository.getCachedBundle()
        bundleState.value = cached
        _uiState.value = _uiState.value.copy(
            bundle = cached ?: _uiState.value.bundle,
            isRefreshingBundle = true,
            errorMessage = null,
            infoMessage = if (cached != null && forceInfoMessage) {
                "Using the cached Peer Data bundle while refreshing."
            } else {
                _uiState.value.infoMessage
            }
        )
        viewModelScope.launch {
            peerDataRepository.refreshBundle()
                .onSuccess { bundle ->
                    bundleState.value = bundle
                    _uiState.value = _uiState.value.copy(
                        bundle = bundle,
                        isRefreshingBundle = false,
                        infoMessage = if (forceInfoMessage) "Peer Data bundle refreshed." else _uiState.value.infoMessage
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingBundle = false,
                        errorMessage = if (_uiState.value.bundle == null) {
                            error.message ?: "Unable to load the Peer Data bundle."
                        } else {
                            null
                        },
                        infoMessage = if (_uiState.value.bundle != null) {
                            "Using the cached Peer Data bundle because refresh failed."
                        } else {
                            _uiState.value.infoMessage
                        }
                    )
                }
        }
    }

    private fun refreshSnapshot(forceInfoMessage: Boolean = false) {
        if (!entitlementState.value.isEnabled) return
        currentUid ?: return
        val cached = peerDataRepository.getCachedSnapshot()
        if (cached != null) {
            snapshotState.value = cached
        }
        _uiState.value = _uiState.value.copy(
            isRefreshingSnapshot = true,
            snapshot = cached ?: _uiState.value.snapshot,
            errorMessage = null,
            infoMessage = if (forceInfoMessage) "Refreshing peer benchmarks." else _uiState.value.infoMessage
        )
        viewModelScope.launch {
            peerDataRepository.getPeerSnapshot()
                .onSuccess { snapshot ->
                    snapshotState.value = snapshot
                    _uiState.value = _uiState.value.copy(
                        snapshot = snapshot,
                        isRefreshingSnapshot = false,
                        infoMessage = if (forceInfoMessage) "Peer benchmarks refreshed." else _uiState.value.infoMessage
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshingSnapshot = false,
                        errorMessage = if (_uiState.value.snapshot == null) {
                            error.message ?: "Unable to load peer benchmarks."
                        } else {
                            null
                        },
                        infoMessage = if (_uiState.value.snapshot != null) {
                            "Using the cached peer snapshot because refresh failed."
                        } else {
                            _uiState.value.infoMessage
                        }
                    )
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PeerDataViewModel(
                    authRepository = AppModule.authRepository,
                    peerDataRepository = AppModule.peerDataRepository
                ) as T
            }
        }
    }
}

private data class PeerObservedData(
    val userFlag: Boolean?,
    val settings: PeerDataParticipationSettings,
    val entitlement: PeerDataEntitlementState,
    val bundle: PeerDataBundle?,
    val snapshot: PeerDataSnapshot?
)
