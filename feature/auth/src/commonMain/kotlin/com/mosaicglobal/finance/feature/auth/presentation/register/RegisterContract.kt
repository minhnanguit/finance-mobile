package com.mosaicglobal.finance.feature.auth.presentation.register

import com.mosaicglobal.finance.core.presentation.text.UiText

data class RegisterState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val displayNameError: UiText? = null,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val error: UiText? = null,
) {
    val canSubmit: Boolean
        get() = displayName.isNotBlank() && email.isNotBlank() && password.isNotEmpty() && !isSubmitting
}

sealed interface RegisterIntent {
    data class DisplayNameChanged(val value: String) : RegisterIntent
    data class EmailChanged(val value: String) : RegisterIntent
    data class PasswordChanged(val value: String) : RegisterIntent
    data object Submit : RegisterIntent
    data object LoginClicked : RegisterIntent
    data object ErrorDismissed : RegisterIntent
}

sealed interface RegisterEffect {
    data object NavigateToHome : RegisterEffect
    data object NavigateToLogin : RegisterEffect
}
