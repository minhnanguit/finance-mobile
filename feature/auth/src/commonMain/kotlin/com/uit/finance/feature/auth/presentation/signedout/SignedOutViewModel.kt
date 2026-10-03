package com.uit.finance.feature.auth.presentation.signedout

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toUiText
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.usecase.SignInUseCase
import kotlinx.coroutines.launch

internal class SignedOutViewModel(
    private val signIn: SignInUseCase,
) : MviViewModel<SignedOutState, SignedOutIntent, SignedOutEffect>(SignedOutState()) {

    override fun onIntent(intent: SignedOutIntent) {
        when (intent) {
            SignedOutIntent.SignInClicked -> start(SignInMode.SignIn)
            SignedOutIntent.SignUpClicked -> start(SignInMode.SignUp)
            SignedOutIntent.ErrorDismissed -> setState { copy(error = null) }
        }
    }

    private fun start(mode: SignInMode) {
        // Một flow browser mỗi lúc: bấm liên tục không được mở nhiều trang login.
        if (currentState.isBusy) return
        setState { copy(inProgress = mode, error = null) }
        viewModelScope.launch {
            signIn(mode).fold(
                onSuccess = { result ->
                    setState { copy(inProgress = null) }
                    if (result is SignInResult.SignedIn) sendEffect(SignedOutEffect.NavigateToHome)
                },
                onFailure = { error -> setState { copy(inProgress = null).withError(error) } },
            )
        }
    }
}

internal fun SignedOutState.withError(error: AppError): SignedOutState = when (error) {
    // Code hết hạn / bị dùng lại: người dùng chỉ cần thử lại.
    AppError.Unauthorized -> copy(error = UiText.Key("auth.sign_in_failed", "Đăng nhập chưa hoàn tất. Vui lòng thử lại."))
    else -> copy(error = error.toUiText())
}
