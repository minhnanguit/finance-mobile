package com.mosaicglobal.finance.feature.auth.presentation.signedout

import com.mosaicglobal.finance.core.presentation.text.UiText
import com.mosaicglobal.finance.feature.auth.domain.model.SignInMode

data class SignedOutState(
    /** Nút nào đang chờ browser trả về; `null` khi rảnh. */
    val inProgress: SignInMode? = null,
    val error: UiText? = null,
) {
    val isBusy: Boolean get() = inProgress != null
}

sealed interface SignedOutIntent {
    data object SignInClicked : SignedOutIntent
    data object SignUpClicked : SignedOutIntent
    data object ErrorDismissed : SignedOutIntent
}

sealed interface SignedOutEffect {
    data object NavigateToHome : SignedOutEffect
}
