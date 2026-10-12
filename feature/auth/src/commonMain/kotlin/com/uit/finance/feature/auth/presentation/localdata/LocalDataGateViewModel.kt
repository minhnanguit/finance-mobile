package com.uit.finance.feature.auth.presentation.localdata

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.feature.auth.domain.model.LocalDataStatus
import com.uit.finance.feature.auth.domain.usecase.ObserveLocalDataStatusUseCase
import com.uit.finance.feature.auth.domain.usecase.RetryLocalDataUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Màn chặn S6: chờ sổ của phiên mở được; sổ sẵn sàng thì báo app vào trang chủ. */
internal class LocalDataGateViewModel(
    observeStatus: ObserveLocalDataStatusUseCase,
    private val retry: RetryLocalDataUseCase,
) : MviViewModel<LocalDataGateState, LocalDataGateIntent, LocalDataGateEffect>(LocalDataGateState()) {

    init {
        observeStatus()
            .onEach { status ->
                setState { copy(status = status, isRetrying = status == LocalDataStatus.Preparing && isRetrying) }
                if (status == LocalDataStatus.Ready) sendEffect(LocalDataGateEffect.Ready)
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: LocalDataGateIntent) = when (intent) {
        LocalDataGateIntent.RetryClicked -> {
            setState { copy(isRetrying = true) }
            retry()
        }
        LocalDataGateIntent.LogoutClicked -> sendEffect(LocalDataGateEffect.OpenProfile)
    }
}
