package com.example.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.mocks.AuthRepository
import com.example.app.mocks.FakeAuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val generalError: String? = null
) {
    fun isSubmitEnabled(isRegistration: Boolean): Boolean {
        if (isLoading) return false
        if (email.isBlank() || password.isBlank()) return false
        if (emailError != null || passwordError != null) return false
        if (isRegistration) {
            if (confirmPassword.isBlank() || confirmPasswordError != null) return false
        }
        return true
    }
}

class AuthViewModel(
    private val authRepository: AuthRepository = FakeAuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>()
    val events = _events.receiveAsFlow()

    // --- Поля ---

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, generalError = null) }
        validateEmail(value)?.let { error ->
            _uiState.update { it.copy(emailError = error) }
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null, generalError = null) }
        validatePassword(value)?.let { error ->
            _uiState.update { it.copy(passwordError = error) }
        }
        // Если поле подтверждения уже заполнено — перепроверяем совпадение
        val confirm = _uiState.value.confirmPassword
        if (confirm.isNotEmpty()) {
            val confirmError = validateConfirm(value, confirm)
            _uiState.update { it.copy(confirmPasswordError = confirmError) }
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = null) }
        val password = _uiState.value.password
        validateConfirm(password, value)?.let { error ->
            _uiState.update { it.copy(confirmPasswordError = error) }
        }
    }

    fun onTogglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    // --- Отправка ---

    fun onSubmit(isRegistration: Boolean) {
        val state = _uiState.value

        val emailError = validateEmail(state.email)
        val passwordError = validatePassword(state.password)
        val confirmError = if (isRegistration) {
            validateConfirm(state.password, state.confirmPassword)
        } else null

        if (emailError != null || passwordError != null || confirmError != null) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = if (isRegistration) {
                authRepository.register(state.email, state.password)
            } else {
                authRepository.login(state.email, state.password)
            }

            result
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(AuthEvent.NavigateToSuccess)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalError = throwable.message ?: "Что-то пошло не так"
                        )
                    }
                }
        }
    }

    fun onErrorDismiss() {
        _uiState.update { it.copy(generalError = null) }
    }

    // --- Валидация ---

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Введите почту"
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
            "Некорректная почта"
        else -> null
    }

    private fun validatePassword(password: String): String? = when {
        password.isBlank() -> "Введите пароль"
        password.length < 6 -> "Минимум 6 символов"
        else -> null
    }

    private fun validateConfirm(password: String, confirm: String): String? = when {
        confirm.isBlank() -> "Повторите пароль"
        confirm != password -> "Пароли не совпадают"
        else -> null
    }
}

sealed interface AuthEvent {
    data object NavigateToSuccess : AuthEvent
}