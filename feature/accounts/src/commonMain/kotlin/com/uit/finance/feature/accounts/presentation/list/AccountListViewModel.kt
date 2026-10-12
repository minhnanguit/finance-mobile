package com.uit.finance.feature.accounts.presentation.list

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.ui.ledger.toLedgerUiText
import com.uit.finance.feature.accounts.domain.model.AccountWithBalance
import com.uit.finance.feature.accounts.domain.model.AccountsOverview
import com.uit.finance.feature.accounts.domain.model.MoveDirection
import com.uit.finance.feature.accounts.domain.usecase.DeleteAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.MoveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.ObserveAccountsOverviewUseCase
import com.uit.finance.feature.accounts.domain.usecase.SetAccountArchivedUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class AccountListViewModel(
    observeOverview: ObserveAccountsOverviewUseCase,
    private val setArchived: SetAccountArchivedUseCase,
    private val moveAccount: MoveAccountUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val money: MoneyFormatter,
) : MviViewModel<AccountListState, AccountListIntent, AccountListEffect>(AccountListState()) {

    init {
        observeOverview()
            .onEach { overview -> setState { withOverview(overview) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: AccountListIntent) = when (intent) {
        AccountListIntent.AddClicked -> sendEffect(AccountListEffect.OpenEditor(null))
        is AccountListIntent.EditClicked -> sendEffect(AccountListEffect.OpenEditor(intent.accountId))
        is AccountListIntent.ArchiveClicked -> execute(
            done = if (intent.archived) UiText.Key("accounts.archived", "Đã lưu trữ ví.") else null,
        ) { setArchived(intent.accountId, intent.archived) }
        is AccountListIntent.MoveUpClicked -> execute { moveAccount(intent.accountId, MoveDirection.Up) }
        is AccountListIntent.MoveDownClicked -> execute { moveAccount(intent.accountId, MoveDirection.Down) }
        is AccountListIntent.DeleteClicked ->
            setState { copy(pendingDelete = (active + archived).firstOrNull { it.id == intent.accountId }) }
        AccountListIntent.DeleteConfirmed -> confirmDelete()
        AccountListIntent.DeleteDismissed -> setState { copy(pendingDelete = null) }
        AccountListIntent.ToggleArchived -> setState { copy(showArchived = !showArchived) }
        AccountListIntent.MessageShown -> setState { copy(message = null) }
        AccountListIntent.BackClicked -> sendEffect(AccountListEffect.NavigateBack)
    }

    private fun confirmDelete() {
        val target = currentState.pendingDelete ?: return
        setState { copy(pendingDelete = null) }
        execute(done = UiText.Key("accounts.deleted", "Đã xoá ví.")) { deleteAccount(target.id) }
    }

    /** Ghi xong thì Flow tự phát bản mới; chỉ cần báo lỗi (hoặc câu xác nhận). */
    private fun execute(done: UiText? = null, action: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            action().fold(
                onSuccess = { done?.let { setState { copy(message = it) } } },
                onFailure = { error -> setState { copy(message = error.toLedgerUiText()) } },
            )
        }
    }

    private fun AccountListState.withOverview(overview: AccountsOverview): AccountListState {
        val lastIndex = overview.active.lastIndex
        return copy(
            isLoading = false,
            totals = overview.totals.map { it.toUi() },
            active = overview.active.mapIndexed { index, item -> item.toUi(canMoveUp = index > 0, canMoveDown = index < lastIndex) },
            archived = overview.archived.map { it.toUi(canMoveUp = false, canMoveDown = false) },
        )
    }

    private fun AccountWithBalance.toUi(canMoveUp: Boolean, canMoveDown: Boolean) = AccountItemUi(
        id = account.id,
        name = account.name,
        type = account.type,
        balance = balance.toUi(),
        archived = account.archived,
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
    )

    private fun Money.toUi(): MoneyUi = money.format(this).let { MoneyUi(it.currency, it.amount, it.isNegative) }
}
