package com.uit.finance.feature.auth.presentation.profile

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.feature.auth.domain.model.UserProfile

data class ProfileState(
    val isLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val profile: UserProfile? = null,
    val error: UiText? = null,
)

sealed interface ProfileIntent {
    data object Retry : ProfileIntent
    data object Logout : ProfileIntent
}

sealed interface ProfileEffect {
    data object LoggedOut : ProfileEffect
}
