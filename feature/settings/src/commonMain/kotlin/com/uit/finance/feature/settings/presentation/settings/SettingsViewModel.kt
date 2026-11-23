@file:OptIn(ExperimentalTime::class)

package com.uit.finance.feature.settings.presentation.settings

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toUiText
import com.uit.finance.feature.settings.domain.model.AccountProfile
import com.uit.finance.feature.settings.domain.model.PasswordChange
import com.uit.finance.feature.settings.domain.model.SettingsAppInfo
import com.uit.finance.feature.settings.domain.usecase.ChangePasswordUseCase
import com.uit.finance.feature.settings.domain.usecase.CountUnsentChangesUseCase
import com.uit.finance.feature.settings.domain.usecase.LogoutUseCase
import com.uit.finance.feature.settings.domain.usecase.ObserveAccountProfileUseCase
import com.uit.finance.feature.settings.domain.usecase.ObserveReminderSettingsUseCase
import com.uit.finance.feature.settings.domain.usecase.SetReminderEnabledUseCase
import com.uit.finance.feature.settings.domain.usecase.SetReminderTimeUseCase
import com.uit.finance.feature.settings.domain.usecase.GetNotificationPermissionUseCase
import com.uit.finance.feature.settings.domain.usecase.OpenNotificationSettingsUseCase
import com.uit.finance.feature.settings.domain.model.ReminderToggleResult
import com.uit.finance.core.notification.NotificationPermissionStatus
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

internal class SettingsViewModel(
    observeProfile: ObserveAccountProfileUseCase,
    private val appInfo: SettingsAppInfo,
    private val changePassword: ChangePasswordUseCase,
    private val countUnsentChanges: CountUnsentChangesUseCase,
    private val logout: LogoutUseCase,
    observeReminderSettings: ObserveReminderSettingsUseCase,
    private val setReminderEnabled: SetReminderEnabledUseCase,
    private val setReminderTime: SetReminderTimeUseCase,
    private val getNotificationPermission: GetNotificationPermissionUseCase,
    private val openNotificationSettings: OpenNotificationSettingsUseCase,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState(versionName = appInfo.versionName)) {

    init {
        observeProfile()
            .onEach { profile -> setState { copy(profile = profile.toUi()) } }
            .launchIn(viewModelScope)
        observeReminderSettings()
            .onEach { settings ->
                setState {
                    copy(reminder = reminder.copy(
                        enabled = settings.enabled,
                        hour = settings.time.hour,
                        minute = settings.time.minute,
                        timeLabel = settings.time.toString(),
                    ))
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.ChangePasswordClicked -> startPasswordChange()
            SettingsIntent.PrivacyPolicyClicked -> sendEffect(SettingsEffect.OpenUrl(appInfo.privacyPolicyUrl))
            SettingsIntent.DeleteAccountClicked -> sendEffect(SettingsEffect.OpenDeleteAccount)
            SettingsIntent.AutoCaptureClicked -> sendEffect(SettingsEffect.OpenAutoCapture)
            SettingsIntent.LogoutClicked -> requestLogout()
            SettingsIntent.LogoutConfirmed -> {
                setState { copy(unsentChangesWarning = null) }
                performLogout()
            }
            SettingsIntent.LogoutWarningDismissed -> setState { copy(unsentChangesWarning = null) }
            SettingsIntent.MessageShown -> setState { copy(message = null) }
            is SettingsIntent.ReminderToggled -> toggleReminder(intent.enabled)
            SettingsIntent.ReminderTimeClicked -> setState { copy(isTimePickerOpen = true) }
            is SettingsIntent.ReminderTimePicked -> updateReminderTime(intent.hour, intent.minute)
            SettingsIntent.ReminderTimeDismissed -> setState { copy(isTimePickerOpen = false) }
            SettingsIntent.NotificationSettingsClicked -> openNotificationSettings()
            SettingsIntent.ScreenResumed -> refreshNotificationPermission()
        }
    }

    private fun toggleReminder(enabled: Boolean) {
        viewModelScope.launch {
            when (setReminderEnabled(enabled)) {
                ReminderToggleResult.Saved -> Unit
                ReminderToggleResult.PermissionDenied -> setState {
                    copy(message = UiText.Key(
                        "settings.reminder.permission_denied",
                        "Bạn chưa cho phép thông báo. Mở cài đặt máy để bật",
                    ))
                }
            }
            refreshNotificationPermission()
        }
    }

    private fun updateReminderTime(hour: Int, minute: Int) {
        setState { copy(isTimePickerOpen = false) }
        viewModelScope.launch { setReminderTime(hour, minute) }
    }

    private fun refreshNotificationPermission() {
        viewModelScope.launch {
            val blocked = currentState.reminder.enabled &&
                getNotificationPermission() == NotificationPermissionStatus.Denied
            setState { copy(reminder = reminder.copy(notificationsBlocked = blocked)) }
        }
    }

    private fun startPasswordChange() {
        if (currentState.isChangingPassword) return
        setState { copy(isChangingPassword = true) }
        viewModelScope.launch {
            val message = changePassword().fold(
                onSuccess = { it.toMessage() },
                onFailure = { it.toUiText() },
            )
            setState { copy(isChangingPassword = false, message = message ?: this.message) }
        }
    }

    /** Đăng xuất xoá sổ trên máy: còn thay đổi chưa gửi thì hỏi trước, vì chúng sẽ mất. */
    private fun requestLogout() {
        if (currentState.isLoggingOut) return
        viewModelScope.launch {
            val unsent = countUnsentChanges()
            if (unsent > 0) setState { copy(unsentChangesWarning = unsent) } else performLogout()
        }
    }

    private fun performLogout() {
        if (currentState.isLoggingOut) return
        setState { copy(isLoggingOut = true) }
        viewModelScope.launch {
            logout().fold(
                onSuccess = { sendEffect(SettingsEffect.LoggedOut) },
                onFailure = { error -> setState { copy(isLoggingOut = false, message = error.toUiText()) } },
            )
        }
    }

    private fun AccountProfile.toUi(): ProfileUi = ProfileUi(
        displayName = displayName.ifBlank { email.orEmpty() },
        email = email,
        memberSince = memberSince?.let { joined ->
            val date = joined.toLocalDateTime(TimeZone.currentSystemDefault()).date
            "Thành viên từ ${date.month.number.toString().padStart(2, '0')}/${date.year}"
        },
    )
}

/** Huỷ thì im lặng. */
private fun PasswordChange.toMessage(): UiText? = when (this) {
    PasswordChange.Changed -> UiText.Key("settings.password.changed", "Đã đổi mật khẩu")
    PasswordChange.Cancelled -> null
    PasswordChange.DifferentAccount -> UiText.Key(
        "settings.password.different_account",
        "Trình duyệt đang đăng nhập tài khoản khác. Mật khẩu của tài khoản này chưa đổi",
    )
}
