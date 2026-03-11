package com.sidekick.opt_pal.feature.policy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.PolicyAlertAvailability
import com.sidekick.opt_pal.data.model.PolicyAlertAudience
import com.sidekick.opt_pal.data.model.PolicyAlertCard
import com.sidekick.opt_pal.data.model.PolicyAlertSeverity
import com.sidekick.opt_pal.data.model.PolicyAlertState
import com.sidekick.opt_pal.data.model.PolicyAlertTopic
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.PolicyAlertRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class PolicyAlertFilter {
    ALL,
    CRITICAL,
    TRAVEL,
    EMPLOYMENT,
    REPORTING,
    APPLICATIONS
}

data class PolicyAlertFeedUiState(
    val isLoading: Boolean = true,
    val availability: PolicyAlertAvailability = PolicyAlertAvailability(),
    val profile: UserProfile? = null,
    val alerts: List<PolicyAlertCard> = emptyList(),
    val alertStates: List<PolicyAlertState> = emptyList(),
    val selectedFilter: PolicyAlertFilter = PolicyAlertFilter.ALL,
    val selectedAlertId: String? = null,
    val policyNotificationsEnabled: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val selectedAlert: PolicyAlertCard?
        get() = visibleAlerts.firstOrNull { it.id == selectedAlertId } ?: visibleAlerts.firstOrNull()

    val unreadCount: Int
        get() = visibleAlerts.count { alert -> alertStates.none { it.alertId == alert.id && it.openedAt != null } }

    val visibleAlerts: List<PolicyAlertCard>
        get() {
            val filtered = alerts.filter { alert ->
                matchesAudience(alert, profile) && matchesFilter(alert, selectedFilter)
            }
            return filtered.sortedWith(
                compareByDescending<PolicyAlertCard> { it.isCritical }
                    .thenByDescending { alert -> alertStates.none { it.alertId == alert.id && it.openedAt != null } }
                    .thenByDescending { it.publishedAt }
            )
        }

    private fun matchesAudience(alert: PolicyAlertCard, profile: UserProfile?): Boolean {
        return when (alert.parsedAudience) {
            PolicyAlertAudience.INITIAL_OPT -> profile?.optType?.lowercase() != "stem"
            PolicyAlertAudience.STEM_OPT -> profile?.optType?.lowercase() == "stem"
            else -> true
        }
    }

    private fun matchesFilter(alert: PolicyAlertCard, filter: PolicyAlertFilter): Boolean {
        return when (filter) {
            PolicyAlertFilter.ALL -> true
            PolicyAlertFilter.CRITICAL -> alert.parsedSeverity == PolicyAlertSeverity.CRITICAL
            PolicyAlertFilter.TRAVEL -> alert.parsedTopics.contains(PolicyAlertTopic.TRAVEL)
            PolicyAlertFilter.EMPLOYMENT -> alert.parsedTopics.contains(PolicyAlertTopic.EMPLOYMENT)
            PolicyAlertFilter.REPORTING -> alert.parsedTopics.contains(PolicyAlertTopic.REPORTING)
            PolicyAlertFilter.APPLICATIONS -> alert.parsedTopics.contains(PolicyAlertTopic.APPLICATIONS)
        }
    }
}

class PolicyAlertFeedViewModel(
    private val authRepository: AuthRepository,
    private val policyAlertRepository: PolicyAlertRepository,
    private val selectedAlertIdArg: String?,
    private val initialFilterArg: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PolicyAlertFeedUiState(
            policyNotificationsEnabled = policyAlertRepository.isNotificationPreferenceEnabled(),
            selectedFilter = initialFilterArg.toFilter()
        )
    )
    val uiState = _uiState.asStateFlow()

    private var currentUid: String? = null
    private var feedObservationJob: Job? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        authRepository.getAuthState()
            .onEach { user ->
                feedObservationJob?.cancel()
                feedObservationJob = null
                currentUid = user?.uid
                if (user == null) {
                    _uiState.value = PolicyAlertFeedUiState(
                        isLoading = false,
                        availability = PolicyAlertAvailability(isEnabled = false, message = "User not logged in."),
                        policyNotificationsEnabled = policyAlertRepository.isNotificationPreferenceEnabled(),
                        errorMessage = "User not logged in."
                    )
                } else {
                    resolveAvailabilityAndObserveFeed(user.uid)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun resolveAvailabilityAndObserveFeed(uid: String) {
        viewModelScope.launch {
            policyAlertRepository.resolveAvailability()
                .onSuccess { availability ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = availability.isEnabled,
                        availability = availability,
                        profile = if (availability.isEnabled) _uiState.value.profile else null,
                        alerts = if (availability.isEnabled) _uiState.value.alerts else emptyList(),
                        alertStates = if (availability.isEnabled) _uiState.value.alertStates else emptyList(),
                        selectedAlertId = if (availability.isEnabled) _uiState.value.selectedAlertId else null,
                        errorMessage = if (availability.isEnabled) _uiState.value.errorMessage else null,
                        infoMessage = if (availability.isEnabled) _uiState.value.infoMessage else null
                    )
                    if (availability.isEnabled) {
                        observeFeed(uid)
                    }
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        availability = PolicyAlertAvailability(
                            isEnabled = false,
                            message = throwable.message ?: "Unable to load Policy Alert Feed."
                        ),
                        profile = null,
                        alerts = emptyList(),
                        alertStates = emptyList(),
                        selectedAlertId = null,
                        infoMessage = null
                    )
                }
        }
    }

    private fun observeFeed(uid: String) {
        feedObservationJob = combine(
            authRepository.getUserProfile(uid),
            policyAlertRepository.observePublishedAlerts(),
            policyAlertRepository.observeAlertStates(uid)
        ) { profile, alerts, states ->
            Triple(profile, alerts, states)
        }.onEach { (profile, alerts, states) ->
            val relevantAlerts = alerts.filterNot { it.source.url.isBlank() }
            val selectedAlertId = when {
                !selectedAlertIdArg.isNullOrBlank() && relevantAlerts.any { it.id == selectedAlertIdArg } -> selectedAlertIdArg
                _uiState.value.selectedAlertId != null && relevantAlerts.any { it.id == _uiState.value.selectedAlertId } ->
                    _uiState.value.selectedAlertId
                else -> relevantAlerts.firstOrNull()?.id
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                profile = profile,
                alerts = relevantAlerts,
                alertStates = states,
                selectedAlertId = selectedAlertId,
                policyNotificationsEnabled = policyAlertRepository.isNotificationPreferenceEnabled()
            )
            selectedAlertId?.let { maybeMarkOpened(it) }
        }.launchIn(viewModelScope)
    }

    fun selectFilter(filter: PolicyAlertFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun selectAlert(alertId: String) {
        _uiState.value = _uiState.value.copy(selectedAlertId = alertId)
        AnalyticsLogger.logPolicyAlertOpened(alertId)
        maybeMarkOpened(alertId)
    }

    fun enablePolicyNotifications() {
        viewModelScope.launch {
            policyAlertRepository.syncNotificationPreference(enabled = true)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        policyNotificationsEnabled = true,
                        infoMessage = "Policy alert notifications enabled."
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = throwable.message ?: "Unable to enable policy-alert notifications."
                    )
                }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }

    private fun maybeMarkOpened(alertId: String) {
        val uid = currentUid ?: return
        val alreadyOpened = _uiState.value.alertStates.any { it.alertId == alertId && it.openedAt != null }
        if (alreadyOpened) return
        viewModelScope.launch {
            policyAlertRepository.markAlertOpened(uid, alertId)
        }
    }

    companion object {
        fun provideFactory(selectedAlertId: String?, initialFilter: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PolicyAlertFeedViewModel(
                        authRepository = AppModule.authRepository,
                        policyAlertRepository = AppModule.policyAlertRepository,
                        selectedAlertIdArg = selectedAlertId,
                        initialFilterArg = initialFilter
                    ) as T
                }
            }
    }
}

private fun String?.toFilter(): PolicyAlertFilter {
    return when (this?.lowercase()) {
        "critical" -> PolicyAlertFilter.CRITICAL
        "travel" -> PolicyAlertFilter.TRAVEL
        "employment" -> PolicyAlertFilter.EMPLOYMENT
        "reporting" -> PolicyAlertFilter.REPORTING
        "applications" -> PolicyAlertFilter.APPLICATIONS
        else -> PolicyAlertFilter.ALL
    }
}
