package com.sidekick.opt_pal.core.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class SessionViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val uiState: StateFlow<SessionUiState> = authRepository.getAuthState()
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(SessionUiState(isLoggedIn = false, isCheckingAuth = false))
            } else {
                authRepository.getUserProfile(user.uid).toSessionStateFlow()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SessionUiState(isCheckingAuth = true)
        )

    private fun Flow<UserProfile?>.toSessionStateFlow(): Flow<SessionUiState> {
        return this
            .map { profile ->
                SessionUiState(
                    isLoggedIn = true,
                    isProfileComplete = profile.isComplete(),
                    userProfile = profile,
                    isCheckingAuth = false
                )
            }
            .onStart {
                emit(SessionUiState(isLoggedIn = true, isCheckingAuth = true))
            }
            .catch { throwable ->
                emit(
                    SessionUiState(
                        isLoggedIn = true,
                        isProfileComplete = false,
                        isCheckingAuth = false,
                        errorMessage = throwable.message
                    )
                )
            }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionViewModel(AppModule.authRepository) as T
            }
        }
    }
}

private fun UserProfile?.isComplete(): Boolean {
    return this?.let { it.optType != null && it.optStartDate != null } ?: false
}

data class SessionUiState(
    val isLoggedIn: Boolean = false,
    val isProfileComplete: Boolean = false,
    val isCheckingAuth: Boolean = true,
    val userProfile: UserProfile? = null,
    val errorMessage: String? = null
)
