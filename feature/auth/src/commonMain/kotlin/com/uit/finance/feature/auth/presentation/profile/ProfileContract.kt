package com.uit.finance.feature.auth.presentation.profile

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.feature.auth.domain.model.UserProfile

data class ProfileState(
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val profile: UserProfile? = null,
    val error: UiText? = null,
    val errorAction: ProfileErrorAction = ProfileErrorAction.ReloadProfile,
    /** Khác `null`: còn chừng này thay đổi chưa gửi, hỏi user trước khi đăng xuất (ADR-006 B5). */
    val unsentChangesWarning: Long? = null,
)

enum class ProfileErrorAction { ReloadProfile, RetryLogout }

sealed interface ProfileIntent {
    data object Retry : ProfileIntent
    data object Logout : ProfileIntent
    data object ConfirmLogout : ProfileIntent
    data object DismissLogoutWarning : ProfileIntent
}

sealed interface ProfileEffect {
    data object LoggedOut : ProfileEffect
}
