package com.mosaicglobal.finance.feature.auth.presentation.profile

import androidx.lifecycle.viewModelScope
import com.mosaicglobal.finance.core.common.result.fold
import com.mosaicglobal.finance.core.presentation.mvi.MviViewModel
import com.mosaicglobal.finance.core.presentation.text.toUiText
import com.mosaicglobal.finance.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.mosaicglobal.finance.feature.auth.domain.usecase.LogoutUseCase
import kotlinx.coroutines.launch

internal class ProfileViewModel(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val logout: LogoutUseCase,
) : MviViewModel<ProfileState, ProfileIntent, ProfileEffect>(ProfileState()) {

    init {
        load()
    }

    override fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Retry -> load()
            ProfileIntent.Logout -> performLogout()
        }
    }

    private fun load() {
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getCurrentUser().fold(
                onSuccess = { profile -> setState { copy(isLoading = false, profile = profile) } },
                onFailure = { error -> setState { copy(isLoading = false, error = error.toUiText()) } },
            )
        }
    }

    private fun performLogout() {
        if (currentState.isLoggingOut) return
        setState { copy(isLoggingOut = true) }
        viewModelScope.launch {
            logout().fold(
                onSuccess = { sendEffect(ProfileEffect.LoggedOut) },
                onFailure = { error -> setState { copy(isLoggingOut = false, error = error.toUiText()) } },
            )
        }
    }
}
