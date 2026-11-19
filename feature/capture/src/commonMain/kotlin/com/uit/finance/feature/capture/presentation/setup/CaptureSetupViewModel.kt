package com.uit.finance.feature.capture.presentation.setup

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.feature.capture.domain.usecase.GetListenerAccessUseCase
import com.uit.finance.feature.capture.domain.usecase.ObserveCaptureSetupUseCase
import com.uit.finance.feature.capture.domain.usecase.OpenListenerSettingsUseCase
import com.uit.finance.feature.capture.domain.usecase.SetCaptureBanksUseCase
import com.uit.finance.feature.capture.domain.usecase.SetCaptureEnabledUseCase
import com.uit.finance.feature.capture.domain.usecase.SetDefaultCategoryUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class CaptureSetupViewModel(
    observeSetup: ObserveCaptureSetupUseCase,
    private val getAccess: GetListenerAccessUseCase,
    private val openAccessSettings: OpenListenerSettingsUseCase,
    private val setEnabled: SetCaptureEnabledUseCase,
    private val setBanks: SetCaptureBanksUseCase,
    private val setDefaultCategory: SetDefaultCategoryUseCase,
) : MviViewModel<CaptureSetupState, CaptureSetupIntent, CaptureSetupEffect>(CaptureSetupState()) {

    init {
        refreshAccess()
        observeSetup().onEach { data ->
            setState {
                copy(
                    isLoading = false,
                    enabled = data.settings.enabled,
                    availableBanks = data.availableBanks,
                    selectedBanks = data.settings.banks,
                    expenseCategories = data.expenseCategories,
                    incomeCategories = data.incomeCategories,
                    defaultExpenseCategoryId = data.settings.defaultExpenseCategoryId,
                    defaultIncomeCategoryId = data.settings.defaultIncomeCategoryId,
                    pendingCount = data.pendingCount,
                )
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: CaptureSetupIntent) = when (intent) {
        CaptureSetupIntent.ScreenResumed -> refreshAccess()
        is CaptureSetupIntent.EnabledToggled -> {
            viewModelScope.launch { setEnabled(intent.enabled) }
            // Bật mà chưa có quyền: mở luôn cài đặt máy, quay lại thì ScreenResumed đọc lại quyền.
            if (intent.enabled && currentState.listenerSupported && !currentState.accessGranted) openAccessSettings()
            Unit
        }
        CaptureSetupIntent.OpenAccessSettingsClicked -> openAccessSettings()
        is CaptureSetupIntent.BankToggled -> {
            val banks = if (intent.selected) currentState.selectedBanks + intent.bank else currentState.selectedBanks - intent.bank
            viewModelScope.launch { setBanks(banks) }
            Unit
        }
        is CaptureSetupIntent.PickCategoryClicked -> setState { copy(pickingCategory = intent.kind) }
        is CaptureSetupIntent.CategoryPicked -> {
            val kind = currentState.pickingCategory
            setState { copy(pickingCategory = null) }
            if (kind != null) viewModelScope.launch { setDefaultCategory(kind, intent.categoryId) }
            Unit
        }
        CaptureSetupIntent.CategoryPickerDismissed -> setState { copy(pickingCategory = null) }
        CaptureSetupIntent.InboxClicked -> sendEffect(CaptureSetupEffect.OpenInbox)
        CaptureSetupIntent.PasteClicked -> sendEffect(CaptureSetupEffect.OpenPaste)
    }

    private fun refreshAccess() {
        val access = getAccess()
        setState { copy(listenerSupported = access != null, accessGranted = access == true) }
    }
}
