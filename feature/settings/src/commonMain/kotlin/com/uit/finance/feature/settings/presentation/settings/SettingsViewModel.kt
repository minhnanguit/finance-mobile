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
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState(versionName = appInfo.versionName)) {

    init {
        observeProfile()
            .onEach { profile -> setState { copy(profile = profile.toUi()) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.ChangePasswordClicked -> startPasswordChange()
            SettingsIntent.PrivacyPolicyClicked -> sendEffect(SettingsEffect.OpenUrl(appInfo.privacyPolicyUrl))
            SettingsIntent.DeleteAccountClicked -> sendEffect(SettingsEffect.OpenDeleteAccount)
            SettingsIntent.LogoutClicked -> requestLogout()
            SettingsIntent.LogoutConfirmed -> {
                setState { copy(unsentChangesWarning = null) }
                performLogout()
            }
            SettingsIntent.LogoutWarningDismissed -> setState { copy(unsentChangesWarning = null) }
            SettingsIntent.MessageShown -> setState { copy(message = null) }
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
