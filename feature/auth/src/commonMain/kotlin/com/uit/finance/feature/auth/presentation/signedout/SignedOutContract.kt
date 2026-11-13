package com.uit.finance.feature.auth.presentation.signedout

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.feature.auth.domain.model.SignInMode

data class SignedOutState(
    /** Nút nào đang chờ browser trả về; `null` khi rảnh. */
    val inProgress: SignInMode? = null,
    val error: UiText? = null,
    /** Máy còn sổ của tài khoản khác: hỏi có xoá không trước khi vào app (ADR-006 B5). */
    val otherAccountsPrompt: Boolean = false,
    /** Phiên vừa kết thúc vì tài khoản bị xoá (ADR-008 A9): báo một lần. */
    val notice: UiText? = null,
    /** Chưa ai đăng nhập mà máy còn sổ (hết phiên B7, hoặc tài khoản đã bị xoá từ máy khác): cho xoá tay. */
    val localDataOnDevice: Boolean = false,
    val wipeLocalDataPrompt: Boolean = false,
    /** Vừa xoá xong dữ liệu trên máy: báo một lần. */
    val localDataWiped: Boolean = false,
) {
    val isBusy: Boolean get() = inProgress != null
}

sealed interface SignedOutIntent {
    data object SignInClicked : SignedOutIntent
    data object SignUpClicked : SignedOutIntent
    data object ErrorDismissed : SignedOutIntent
    data object WipeOtherAccounts : SignedOutIntent
    data object KeepOtherAccounts : SignedOutIntent
    data object NoticeDismissed : SignedOutIntent
    data object WipeLocalDataClicked : SignedOutIntent
    data object WipeLocalDataConfirmed : SignedOutIntent
    data object WipeLocalDataDismissed : SignedOutIntent
    data object LocalDataWipedDismissed : SignedOutIntent
}

sealed interface SignedOutEffect {
    data object NavigateToHome : SignedOutEffect
}
