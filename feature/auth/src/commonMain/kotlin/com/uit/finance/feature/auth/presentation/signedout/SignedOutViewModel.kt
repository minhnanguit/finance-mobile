package com.uit.finance.feature.auth.presentation.signedout

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.common.result.getOrNull
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toUiText
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.SignInResult
import com.uit.finance.feature.auth.domain.model.SignOutNotice
import com.uit.finance.feature.auth.domain.usecase.AcknowledgeSignOutNoticeUseCase
import com.uit.finance.feature.auth.domain.usecase.CheckLocalDataOnDeviceUseCase
import com.uit.finance.feature.auth.domain.usecase.CheckOtherAccountsDataUseCase
import com.uit.finance.feature.auth.domain.usecase.ObserveSignOutNoticeUseCase
import com.uit.finance.feature.auth.domain.usecase.SignInUseCase
import com.uit.finance.feature.auth.domain.usecase.WipeLocalDataUseCase
import com.uit.finance.feature.auth.domain.usecase.WipeOtherAccountsDataUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class SignedOutViewModel(
    private val signIn: SignInUseCase,
    private val checkOtherAccounts: CheckOtherAccountsDataUseCase,
    private val wipeOtherAccounts: WipeOtherAccountsDataUseCase,
    observeNotice: ObserveSignOutNoticeUseCase,
    private val acknowledgeNotice: AcknowledgeSignOutNoticeUseCase,
    private val checkLocalData: CheckLocalDataOnDeviceUseCase,
    private val wipeLocalData: WipeLocalDataUseCase,
) : MviViewModel<SignedOutState, SignedOutIntent, SignedOutEffect>(SignedOutState()) {

    init {
        observeNotice()
            .onEach { notice -> setState { copy(notice = notice?.toUiText()) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            val hasLocalData = checkLocalData()
            setState { copy(localDataOnDevice = hasLocalData) }
        }
    }

    override fun onIntent(intent: SignedOutIntent) {
        when (intent) {
            SignedOutIntent.SignInClicked -> start(SignInMode.SignIn)
            SignedOutIntent.SignUpClicked -> start(SignInMode.SignUp)
            SignedOutIntent.ErrorDismissed -> setState { copy(error = null) }
            SignedOutIntent.NoticeDismissed -> acknowledgeNotice()
            SignedOutIntent.KeepOtherAccounts -> enterApp()
            SignedOutIntent.WipeOtherAccounts -> viewModelScope.launch {
                wipeOtherAccounts()
                enterApp()
            }
            SignedOutIntent.WipeLocalDataClicked -> if (!currentState.isBusy) setState { copy(wipeLocalDataPrompt = true) }
            SignedOutIntent.WipeLocalDataDismissed -> setState { copy(wipeLocalDataPrompt = false) }
            SignedOutIntent.WipeLocalDataConfirmed -> wipeLocal()
            SignedOutIntent.LocalDataWipedDismissed -> setState { copy(localDataWiped = false) }
        }
    }

    private fun wipeLocal() {
        setState { copy(wipeLocalDataPrompt = false) }
        viewModelScope.launch {
            wipeLocalData().fold(
                onSuccess = { setState { copy(localDataOnDevice = false, localDataWiped = true) } },
                onFailure = { error -> setState { withError(error) } },
            )
        }
    }

    private fun start(mode: SignInMode) {
        // Một flow browser mỗi lúc: bấm liên tục không được mở nhiều trang login.
        if (currentState.isBusy) return
        // Đăng nhập tiếp (tài khoản khác, hoặc tạo mới) thì thông báo cũ không còn liên quan.
        if (currentState.notice != null) acknowledgeNotice()
        setState { copy(inProgress = mode, error = null, localDataWiped = false) }
        viewModelScope.launch {
            signIn(mode).fold(
                onSuccess = { result ->
                    if (result is SignInResult.SignedIn) afterSignIn() else setState { copy(inProgress = null) }
                },
                onFailure = { error -> setState { copy(inProgress = null).withError(error) } },
            )
        }
    }

    /** Máy còn sổ của tài khoản khác thì hỏi trước; kiểm không được (mất mạng) thì vào app luôn. */
    private suspend fun afterSignIn() {
        val others = checkOtherAccounts().getOrNull() ?: 0
        if (others > 0) {
            setState { copy(inProgress = null, otherAccountsPrompt = true) }
        } else {
            enterApp()
        }
    }

    private fun enterApp() {
        setState { copy(inProgress = null, otherAccountsPrompt = false) }
        sendEffect(SignedOutEffect.NavigateToHome)
    }
}

private fun SignOutNotice.toUiText(): UiText = when (this) {
    SignOutNotice.AccountDeleted -> UiText.Key(
        "auth.account_deleted",
        "Tài khoản của bạn đã được xoá cùng toàn bộ dữ liệu trên máy này. Cảm ơn bạn đã dùng Finance.",
    )
}

internal fun SignedOutState.withError(error: AppError): SignedOutState = when (error) {
    // Code hết hạn / bị dùng lại: người dùng chỉ cần thử lại.
    AppError.Unauthorized -> copy(error = UiText.Key("auth.sign_in_failed", "Đăng nhập chưa hoàn tất. Vui lòng thử lại."))
    else -> copy(error = error.toUiText())
}
