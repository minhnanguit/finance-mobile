package com.uit.finance.feature.settings.presentation.deleteaccount

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.feature.settings.domain.model.DeletionConfirmation

data class DeleteAccountState(
    val email: String? = null,
    /** Thay đổi chưa lên server: sẽ mất cùng tài khoản. */
    val unsentChanges: Long = 0,
    val confirmation: String = "",
    /** Đang đăng nhập lại trong trình duyệt hoặc đang gửi yêu cầu xoá. */
    val isDeleting: Boolean = false,
    val error: UiText? = null,
) {
    val isConfirmed: Boolean get() = DeletionConfirmation.matches(confirmation)
    val canSubmit: Boolean get() = isConfirmed && !isDeleting
}

sealed interface DeleteAccountIntent {
    data class ConfirmationChanged(val value: String) : DeleteAccountIntent
    data object Submit : DeleteAccountIntent
    data object BackClicked : DeleteAccountIntent
    data object ErrorDismissed : DeleteAccountIntent
}

sealed interface DeleteAccountEffect {
    /** Tài khoản đã xoá, dữ liệu trên máy đã dọn: về màn đăng nhập. */
    data object Deleted : DeleteAccountEffect
    data object NavigateBack : DeleteAccountEffect
}
