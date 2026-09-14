package com.mosaicglobal.finance.feature.auth.presentation.login

import androidx.lifecycle.viewModelScope
import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.fold
import com.mosaicglobal.finance.core.presentation.mvi.MviViewModel
import com.mosaicglobal.finance.core.presentation.text.UiText
import com.mosaicglobal.finance.core.presentation.text.asUiText
import com.mosaicglobal.finance.core.presentation.text.toUiText
import com.mosaicglobal.finance.feature.auth.domain.usecase.LoginUseCase
import kotlinx.coroutines.launch

internal class LoginViewModel(
    private val loginUseCase: LoginUseCase,
) : MviViewModel<LoginState, LoginIntent, LoginEffect>(LoginState()) {

    override fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> setState { copy(email = intent.value, emailError = null, error = null) }
            is LoginIntent.PasswordChanged -> setState { copy(password = intent.value, passwordError = null, error = null) }
            LoginIntent.Submit -> submit()
            LoginIntent.RegisterClicked -> sendEffect(LoginEffect.NavigateToRegister)
            LoginIntent.ErrorDismissed -> setState { copy(error = null) }
        }
    }

    private fun submit() {
        if (!currentState.canSubmit) return
        setState { copy(isSubmitting = true, error = null, emailError = null, passwordError = null) }
        viewModelScope.launch {
            loginUseCase(currentState.email, currentState.password).fold(
                onSuccess = {
                    setState { copy(isSubmitting = false, password = "") }
                    sendEffect(LoginEffect.NavigateToHome)
                },
                onFailure = { error -> setState { copy(isSubmitting = false).withError(error) } },
            )
        }
    }
}

internal fun LoginState.withError(error: AppError): LoginState = when (error) {
    is AppError.Validation -> copy(
        emailError = error.fieldErrors.firstOrNull { it.field == "email" }?.message?.asUiText(),
        passwordError = error.fieldErrors.firstOrNull { it.field == "password" }?.message?.asUiText(),
        error = error.fieldErrors.firstOrNull { it.field != "email" && it.field != "password" }?.message?.asUiText(),
    )
    AppError.Unauthorized -> copy(error = UiText.Key("auth.invalid_credentials", "Email or password is incorrect."))
    is AppError.Api -> when (error.status) {
        401 -> copy(error = UiText.Key("auth.invalid_credentials", "Email or password is incorrect."))
        429 -> copy(error = UiText.Key("auth.rate_limited", "Too many attempts. Please wait a moment."))
        else -> copy(error = error.toUiText())
    }
    else -> copy(error = error.toUiText())
}
