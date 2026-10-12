package com.uit.finance.feature.accounts.presentation.editor

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.presentation.money.MoneyInput
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.ui.ledger.messageFor
import com.uit.finance.core.ui.ledger.toLedgerUiText
import com.uit.finance.feature.accounts.domain.model.AccountInput
import com.uit.finance.feature.accounts.domain.usecase.ObserveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.SaveAccountUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class AccountEditorViewModel(
    private val args: AccountEditorArgs,
    private val observeAccount: ObserveAccountUseCase,
    private val saveAccount: SaveAccountUseCase,
) : MviViewModel<AccountEditorState, AccountEditorIntent, AccountEditorEffect>(
    AccountEditorState(isLoading = args.accountId != null, isNew = args.accountId == null),
) {

    init {
        args.accountId?.let(::load)
    }

    override fun onIntent(intent: AccountEditorIntent) = when (intent) {
        is AccountEditorIntent.NameChanged -> setState { copy(name = intent.value, nameError = null) }
        is AccountEditorIntent.TypeChanged -> setState { copy(type = intent.value) }
        is AccountEditorIntent.OpeningBalanceChanged -> setState {
            copy(openingBalance = MoneyInput.sanitize(intent.value, currency, allowNegative = true), openingBalanceError = null)
        }
        AccountEditorIntent.CurrencyClicked -> setState { copy(currencyPickerVisible = true) }
        is AccountEditorIntent.CurrencySelected -> setState {
            copy(
                currency = intent.code,
                openingBalance = MoneyInput.convert(openingBalance, intent.code, allowNegative = true),
                currencyPickerVisible = false,
                currencyError = null,
            )
        }
        AccountEditorIntent.CurrencyPickerDismissed -> setState { copy(currencyPickerVisible = false) }
        AccountEditorIntent.SaveClicked -> save()
        AccountEditorIntent.ErrorDismissed -> setState { copy(error = null) }
        AccountEditorIntent.BackClicked -> sendEffect(AccountEditorEffect.Close)
    }

    /** Đọc một lần để điền form; form không tự đổi theo DB khi user đang sửa. */
    private fun load(accountId: String) {
        viewModelScope.launch {
            val account = observeAccount(accountId).first()
            if (account == null) {
                sendEffect(AccountEditorEffect.Close)
                return@launch
            }
            setState {
                copy(
                    isLoading = false,
                    name = account.name,
                    type = account.type,
                    currency = account.currency,
                    openingBalance = MoneyInput.fromMinor(account.openingBalance.amountMinor, account.currency),
                )
            }
        }
    }

    private fun save() {
        val state = currentState
        if (!state.canSave) return
        setState { copy(isSaving = true, error = null) }
        val input = AccountInput(
            id = args.accountId,
            name = state.name,
            type = state.type,
            currency = state.currency,
            openingBalanceMinor = MoneyInput.toMinor(state.openingBalance, state.currency) ?: 0L,
        )
        viewModelScope.launch {
            saveAccount(input).fold(
                onSuccess = { sendEffect(AccountEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false).withError(error) } },
            )
        }
    }

    /** Lỗi theo trường hiện dưới đúng ô; lỗi còn lại hiện ở banner. */
    private fun AccountEditorState.withError(error: AppError): AccountEditorState {
        val fields = (error as? AppError.Validation)?.fieldErrors.orEmpty()
        val nameError = fields.messageFor("name")
        val currencyError = fields.messageFor("currency")
        val balanceError = fields.messageFor("openingBalanceMinor")
        val handled = nameError != null || currencyError != null || balanceError != null
        return copy(
            nameError = nameError,
            currencyError = currencyError,
            openingBalanceError = balanceError,
            error = if (handled) null else error.toLedgerUiText(),
        )
    }
}
