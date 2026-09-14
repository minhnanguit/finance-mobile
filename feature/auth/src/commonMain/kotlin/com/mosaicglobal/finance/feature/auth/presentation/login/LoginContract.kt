package com.mosaicglobal.finance.feature.auth.presentation.login

import com.mosaicglobal.finance.core.presentation.text.UiText

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    val error: UiText? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !isSubmitting
}

sealed interface LoginIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent
    data object Submit : LoginIntent
    data object RegisterClicked : LoginIntent
    data object ErrorDismissed : LoginIntent
}

sealed interface LoginEffect {
    data object NavigateToHome : LoginEffect
    data object NavigateToRegister : LoginEffect
}
