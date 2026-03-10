package com.sidekick.opt_pal.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.update { it.copy(displayName = name, errorMessage = null) }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onLogin() {
        val (email, password) = sanitizeCredentials()
        if (!validateForm(email, password)) return
        performAuth { authRepository.signIn(email, password) }
    }

    fun onCreateAccount() {
        onRegister()
    }

    fun onRegister() {
        val (email, password) = sanitizeCredentials()
        if (!validateForm(email, password)) return
        performAuth { authRepository.register(email, password) }
    }

    private fun sanitizeCredentials(): Pair<String, String> {
        val currentState = _uiState.value
        val email = currentState.email.trim()
        val password = currentState.password.trim()
        if (email != currentState.email || password != currentState.password) {
            _uiState.update { it.copy(email = email, password = password) }
        }
        return email to password
    }

    private fun validateForm(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Email and password cannot be empty.") }
            return false
        }
        return true
    }

    private fun performAuth(action: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = action()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LoginViewModel(AppModule.authRepository) as T
            }
        }
    }
}

data class LoginUiState(
    val email: String = "",
    val displayName: String? = null,
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
