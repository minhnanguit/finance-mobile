package com.mosaicglobal.finance.feature.auth.presentation.register

import androidx.lifecycle.viewModelScope
import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.fold
import com.mosaicglobal.finance.core.presentation.mvi.MviViewModel
import com.mosaicglobal.finance.core.presentation.text.UiText
import com.mosaicglobal.finance.core.presentation.text.asUiText
import com.mosaicglobal.finance.core.presentation.text.toUiText
import com.mosaicglobal.finance.feature.auth.domain.usecase.RegisterUseCase
import kotlinx.coroutines.launch

internal class RegisterViewModel(
    private val registerUseCase: RegisterUseCase,
) : MviViewModel<RegisterState, RegisterIntent, RegisterEffect>(RegisterState()) {

    override fun onIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.DisplayNameChanged -> setState { copy(displayName = intent.value, displayNameError = null, error = null) }
            is RegisterIntent.EmailChanged -> setState { copy(email = intent.value, emailError = null, error = null) }
            is RegisterIntent.PasswordChanged -> setState { copy(password = intent.value, passwordError = null, error = null) }
            RegisterIntent.Submit -> submit()
            RegisterIntent.LoginClicked -> sendEffect(RegisterEffect.NavigateToLogin)
            RegisterIntent.ErrorDismissed -> setState { copy(error = null) }
        }
    }

    private fun submit() {
        if (!currentState.canSubmit) return
        setState { copy(isSubmitting = true, error = null, displayNameError = null, emailError = null, passwordError = null) }
        viewModelScope.launch {
            registerUseCase(
                email = currentState.email,
                password = currentState.password,
                displayName = currentState.displayName,
            ).fold(
                onSuccess = {
                    setState { copy(isSubmitting = false, password = "") }
                    sendEffect(RegisterEffect.NavigateToHome)
                },
                onFailure = { error -> setState { copy(isSubmitting = false).withError(error) } },
            )
        }
    }
}

internal fun RegisterState.withError(error: AppError): RegisterState = when (error) {
    is AppError.Validation -> copy(
        displayNameError = error.fieldErrors.firstOrNull { it.field == "displayName" }?.message?.asUiText(),
        emailError = error.fieldErrors.firstOrNull { it.field == "email" }?.message?.asUiText(),
        passwordError = error.fieldErrors.firstOrNull { it.field == "password" }?.message?.asUiText(),
    )
    is AppError.Api -> when {
        error.status == 409 || error.code == "identity.email_taken" ->
            copy(emailError = UiText.Key("auth.email_taken", "An account with this email already exists."))
        error.fieldErrors.isNotEmpty() -> copy(
            displayNameError = error.fieldErrors.firstOrNull { it.field == "displayName" }?.message?.asUiText(),
            emailError = error.fieldErrors.firstOrNull { it.field == "email" }?.message?.asUiText(),
            passwordError = error.fieldErrors.firstOrNull { it.field == "password" }?.message?.asUiText(),
        )
        else -> copy(error = error.toUiText())
    }
    else -> copy(error = error.toUiText())
}
