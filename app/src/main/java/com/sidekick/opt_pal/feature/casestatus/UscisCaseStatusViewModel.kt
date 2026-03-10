package com.sidekick.opt_pal.feature.casestatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.data.model.UscisCaseRefreshResult
import com.sidekick.opt_pal.data.model.UscisCaseTracker
import com.sidekick.opt_pal.data.model.UscisTrackerAvailability
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.CaseStatusRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class UscisCaseStatusUiState(
    val isLoading: Boolean = true,
    val availability: UscisTrackerAvailability = UscisTrackerAvailability(),
    val cases: List<UscisCaseTracker> = emptyList(),
    val selectedCaseId: String? = null,
    val receiptNumberInput: String = "",
    val isSubmitting: Boolean = false,
    val refreshingCaseId: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null
) {
    val selectedCase: UscisCaseTracker?
        get() = cases.firstOrNull { it.id == selectedCaseId } ?: cases.firstOrNull()
}

class UscisCaseStatusViewModel(
    private val authRepository: AuthRepository,
    private val caseStatusRepository: CaseStatusRepository,
    private val selectedCaseIdArg: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(UscisCaseStatusUiState())
    val uiState = _uiState.asStateFlow()

    private var currentUid: String? = null

    init {
        observeSession()
    }

    private fun observeSession() {
        authRepository.getAuthState()
            .onEach { user ->
                currentUid = user?.uid
                if (user == null) {
                    _uiState.value = UscisCaseStatusUiState(
                        isLoading = false,
                        errorMessage = "User not logged in."
                    )
                } else {
                    loadAvailability()
                    observeCases(user.uid)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCases(uid: String) {
        caseStatusRepository.observeTrackedCases(uid)
            .onEach { cases ->
                val currentState = _uiState.value
                val selectedCaseId = when {
                    currentState.selectedCaseId != null && cases.any { it.id == currentState.selectedCaseId } ->
                        currentState.selectedCaseId
                    !selectedCaseIdArg.isNullOrBlank() && cases.any { it.id == selectedCaseIdArg } ->
                        selectedCaseIdArg
                    else -> cases.firstOrNull()?.id
                }
                _uiState.value = currentState.copy(
                    isLoading = false,
                    cases = cases.filterNot { it.isArchived },
                    selectedCaseId = selectedCaseId
                )
            }
            .launchIn(viewModelScope)
    }

    private fun loadAvailability() {
        viewModelScope.launch {
            caseStatusRepository.getTrackerAvailability()
                .onSuccess { availability ->
                    _uiState.value = _uiState.value.copy(availability = availability, isLoading = false)
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Unable to load USCIS tracker availability."
                    )
                }
        }
    }

    fun onReceiptNumberChanged(value: String) {
        val normalized = value.uppercase().filter { it.isLetterOrDigit() }.take(13)
        _uiState.value = _uiState.value.copy(receiptNumberInput = normalized)
    }

    fun selectCase(caseId: String) {
        _uiState.value = _uiState.value.copy(selectedCaseId = caseId)
    }

    fun addCase() {
        val receiptNumber = _uiState.value.receiptNumberInput.trim().uppercase()
        if (!RECEIPT_NUMBER_REGEX.matches(receiptNumber)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Receipt number must match ABC1234567890."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null, infoMessage = null)
            val result = caseStatusRepository.trackCase(receiptNumber)
            result.onSuccess { caseId ->
                AnalyticsLogger.logScreenView("UscisCaseTracked")
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    receiptNumberInput = "",
                    selectedCaseId = caseId,
                    infoMessage = "Case added. USCIS status is now being tracked."
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = throwable.message ?: "Unable to track this case right now."
                )
            }
        }
    }

    fun refreshSelectedCase() {
        val caseId = _uiState.value.selectedCase?.id ?: return
        refreshCase(caseId)
    }

    fun refreshCase(caseId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(refreshingCaseId = caseId, errorMessage = null, infoMessage = null)
            val result = caseStatusRepository.refreshCase(caseId)
            result.onSuccess { refresh ->
                _uiState.value = _uiState.value.copy(
                    refreshingCaseId = null,
                    infoMessage = refresh.toUserMessage()
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    refreshingCaseId = null,
                    errorMessage = throwable.message ?: "Unable to refresh this case right now."
                )
            }
        }
    }

    fun archiveSelectedCase() {
        val caseId = _uiState.value.selectedCase?.id ?: return
        viewModelScope.launch {
            caseStatusRepository.archiveCase(caseId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        infoMessage = "Case archived.",
                        selectedCaseId = _uiState.value.cases.firstOrNull { it.id != caseId }?.id
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = throwable.message ?: "Unable to archive this case."
                    )
                }
        }
    }

    fun removeSelectedCase() {
        val caseId = _uiState.value.selectedCase?.id ?: return
        viewModelScope.launch {
            caseStatusRepository.removeCase(caseId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        infoMessage = "Case removed.",
                        selectedCaseId = _uiState.value.cases.firstOrNull { it.id != caseId }?.id
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = throwable.message ?: "Unable to remove this case."
                    )
                }
        }
    }

    fun syncMessagingEndpoint() {
        viewModelScope.launch {
            caseStatusRepository.syncMessagingEndpoint()
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null, errorMessage = null)
    }

    companion object {
        private val RECEIPT_NUMBER_REGEX = Regex("^[A-Z]{3}[0-9]{10}$")

        fun provideFactory(selectedCaseId: String?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return UscisCaseStatusViewModel(
                        authRepository = AppModule.authRepository,
                        caseStatusRepository = AppModule.caseStatusRepository,
                        selectedCaseIdArg = selectedCaseId
                    ) as T
                }
            }
    }
}

private fun UscisCaseRefreshResult.toUserMessage(): String {
    return when {
        !refreshed && cooldownRemainingMinutes > 0 ->
            "USCIS refresh is on cooldown. Try again in about $cooldownRemainingMinutes minutes."
        statusChanged ->
            "USCIS reported a status change."
        refreshed ->
            "USCIS status checked. No change detected."
        else ->
            "USCIS status is up to date."
    }
}
