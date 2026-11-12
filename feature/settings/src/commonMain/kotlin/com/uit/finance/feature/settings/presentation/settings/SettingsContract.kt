package com.uit.finance.feature.settings.presentation.settings

import com.uit.finance.core.presentation.text.UiText

data class SettingsState(
    val profile: ProfileUi? = null,
    val versionName: String = "",
    /** Đang mở form đổi mật khẩu trong trình duyệt: chặn bấm lần hai. */
    val isChangingPassword: Boolean = false,
    val isLoggingOut: Boolean = false,
    /** Khác `null`: còn chừng này thay đổi chưa gửi, hỏi trước khi đăng xuất (ADR-006 B5). */
    val unsentChangesWarning: Long? = null,
    /** Thông báo một lần (snackbar). */
    val message: UiText? = null,
)

data class ProfileUi(
    val displayName: String,
    val email: String?,
    /** "Thành viên từ 10/2026"; `null` khi chưa hỏi được server. */
    val memberSince: String?,
)

sealed interface SettingsIntent {
    data object ChangePasswordClicked : SettingsIntent
    data object PrivacyPolicyClicked : SettingsIntent
    data object DeleteAccountClicked : SettingsIntent
    data object LogoutClicked : SettingsIntent
    data object LogoutConfirmed : SettingsIntent
    data object LogoutWarningDismissed : SettingsIntent
    data object MessageShown : SettingsIntent
}

sealed interface SettingsEffect {
    /** Mở trang ngoài app trong trình duyệt hệ thống (chính sách quyền riêng tư). */
    data class OpenUrl(val url: String) : SettingsEffect
    data object OpenDeleteAccount : SettingsEffect
    data object LoggedOut : SettingsEffect
}
