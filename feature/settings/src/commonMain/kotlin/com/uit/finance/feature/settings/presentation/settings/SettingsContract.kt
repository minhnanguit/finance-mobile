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
    val reminder: ReminderUi = ReminderUi(),
    val isTimePickerOpen: Boolean = false,
    /** Thông báo một lần (snackbar). */
    val message: UiText? = null,
)

data class ReminderUi(
    val enabled: Boolean = false,
    val timeLabel: String = "21:00",
    val hour: Int = 21,
    val minute: Int = 0,
    val notificationsBlocked: Boolean = false,
)

data class ProfileUi(
    val displayName: String,
    val email: String?,
    /** "Thành viên từ 10/2026"; `null` khi chưa hỏi được server. */
    val memberSince: String?,
)

sealed interface SettingsIntent {
    data object ChangePasswordClicked : SettingsIntent
    data object AutoCaptureClicked : SettingsIntent
    data object PrivacyPolicyClicked : SettingsIntent
    data object DeleteAccountClicked : SettingsIntent
    data object LogoutClicked : SettingsIntent
    data object LogoutConfirmed : SettingsIntent
    data object LogoutWarningDismissed : SettingsIntent
    data object MessageShown : SettingsIntent
    data class ReminderToggled(val enabled: Boolean) : SettingsIntent
    data object ReminderTimeClicked : SettingsIntent
    data class ReminderTimePicked(val hour: Int, val minute: Int) : SettingsIntent
    data object ReminderTimeDismissed : SettingsIntent
    data object NotificationSettingsClicked : SettingsIntent
    data object ScreenResumed : SettingsIntent
}

sealed interface SettingsEffect {
    /** Mở trang ngoài app trong trình duyệt hệ thống (chính sách quyền riêng tư). */
    data class OpenUrl(val url: String) : SettingsEffect
    data object OpenDeleteAccount : SettingsEffect

    /** Mở cài đặt "Tự ghi từ thông báo ngân hàng" (feature/capture, composeApp map sang destination). */
    data object OpenAutoCapture : SettingsEffect
    data object LoggedOut : SettingsEffect
}
