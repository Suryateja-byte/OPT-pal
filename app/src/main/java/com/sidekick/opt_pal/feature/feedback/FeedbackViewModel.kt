package com.sidekick.opt_pal.feature.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.core.analytics.AnalyticsLogger
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.FeedbackEntry
import com.sidekick.opt_pal.data.repository.FeedbackRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedbackUiState(
    val message: String = "",
    val contactEmail: String = "",
    val includeLogs: Boolean = true,
    val rating: Int = 8,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val submissionSuccess: Boolean = false
)

class FeedbackViewModel(
    private val feedbackRepository: FeedbackRepository,
    private val userSessionProvider: UserSessionProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState = _uiState.asStateFlow()

    fun onMessageChange(value: String) {
        _uiState.update { it.copy(message = value) }
    }

    fun onContactEmailChange(value: String) {
        _uiState.update { it.copy(contactEmail = value) }
    }

    fun onRatingChange(value: Int) {
        _uiState.update { it.copy(rating = value.coerceIn(0, 10)) }
    }

    fun onIncludeLogsChange(value: Boolean) {
        _uiState.update { it.copy(includeLogs = value) }
    }

    fun submitFeedback(deviceInfo: String) {
        val currentState = _uiState.value
        val trimmedMessage = currentState.message.trim()
        if (trimmedMessage.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please share what confused you or broke.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            val entry = FeedbackEntry(
                uid = userSessionProvider.currentUserId,
                message = trimmedMessage,
                contactEmail = currentState.contactEmail.ifBlank { null },
                includeLogs = currentState.includeLogs,
                rating = currentState.rating,
                deviceInfo = deviceInfo
            )
            val result = feedbackRepository.submitFeedback(entry)
            if (result.isSuccess) {
                AnalyticsLogger.logFeedbackSubmitted(currentState.rating)
                _uiState.value = FeedbackUiState(
                    contactEmail = currentState.contactEmail,
                    includeLogs = currentState.includeLogs,
                    rating = currentState.rating,
                    submissionSuccess = true
                )
            } else {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Unable to send feedback right now."
                    )
                }
            }
        }
    }

    fun onErrorConsumed() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onSubmissionHandled() {
        _uiState.update { it.copy(submissionSuccess = false, isSubmitting = false) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FeedbackViewModel(
                    AppModule.feedbackRepository,
                    AppModule.userSessionProvider
                ) as T
            }
        }
    }
}
