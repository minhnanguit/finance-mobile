package com.uit.finance.feature.auth.presentation.profile

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.toUiText
import com.uit.finance.feature.auth.domain.usecase.CountUnsentChangesUseCase
import com.uit.finance.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.uit.finance.feature.auth.domain.usecase.LogoutUseCase
import kotlinx.coroutines.launch

internal class ProfileViewModel(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val logout: LogoutUseCase,
    private val countUnsentChanges: CountUnsentChangesUseCase,
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    init {
        load()
    }

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Retry -> load()
            ProfileIntent.Logout -> requestLogout()
            ProfileIntent.ConfirmLogout -> {
                setState { copy(unsentChangesWarning = null) }
                performLogout()
            }
            ProfileIntent.DismissLogoutWarning -> setState { copy(unsentChangesWarning = null) }
        }
    }

    private fun load() {
        setState { copy(isLoading = true, error = null, errorAction = ProfileErrorAction.ReloadProfile) }
        viewModelScope.launch {
            getCurrentUser().fold(
                onSuccess = { profile -> setState { copy(isLoading = false, profile = profile) } },
                onFailure = { error -> setState { copy(isLoading = false, error = error.toUiText(), errorAction = ProfileErrorAction.ReloadProfile) } },
            )
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
        setState { copy(isLoggingOut = true, error = null) }
        viewModelScope.launch {
            logout().fold(
                onSuccess = { sendEffect(ProfileEffect.LoggedOut) },
                onFailure = { error -> setState { copy(isLoggingOut = false, error = error.toUiText(), errorAction = ProfileErrorAction.RetryLogout) } },
            )
        }
    }
}
