package com.uit.finance.feature.auth.presentation.signedout

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.feature.auth.domain.model.SignInMode

data class SignedOutState(
    /** Nút nào đang chờ browser trả về; `null` khi rảnh. */
    val inProgress: SignInMode? = null,
    val error: UiText? = null,
    /** Máy còn sổ của tài khoản khác: hỏi có xoá không trước khi vào app (ADR-006 B5). */
    val otherAccountsPrompt: Boolean = false,
) {
    val isBusy: Boolean get() = inProgress != null
}

sealed interface SignedOutIntent {
    data object SignInClicked : SignedOutIntent
    data object SignUpClicked : SignedOutIntent
    data object ErrorDismissed : SignedOutIntent
    data object WipeOtherAccounts : SignedOutIntent
    data object KeepOtherAccounts : SignedOutIntent
}

sealed interface SignedOutEffect {
    data object NavigateToHome : SignedOutEffect
}
