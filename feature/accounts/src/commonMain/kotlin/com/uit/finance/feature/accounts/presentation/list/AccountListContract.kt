package com.uit.finance.feature.accounts.presentation.list

import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.presentation.text.UiText

data class AccountListState(
    val isLoading: Boolean = true,
    /** Tổng số dư theo tiền tệ của ví đang dùng. */
    val totals: List<MoneyUi> = emptyList(),
    val active: List<AccountItemUi> = emptyList(),
    val archived: List<AccountItemUi> = emptyList(),
    val showArchived: Boolean = false,
    /** Ví đang chờ user xác nhận xoá. */
    val pendingDelete: AccountItemUi? = null,
    val message: UiText? = null,
)

data class MoneyUi(val currency: String, val amount: String, val isNegative: Boolean)

data class AccountItemUi(
    val id: String,
    val name: String,
    val type: AccountType,
    val balance: MoneyUi,
    val archived: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

sealed interface AccountListIntent {
    data object AddClicked : AccountListIntent
    data class EditClicked(val accountId: String) : AccountListIntent
    data class ArchiveClicked(val accountId: String, val archived: Boolean) : AccountListIntent
    data class MoveUpClicked(val accountId: String) : AccountListIntent
    data class MoveDownClicked(val accountId: String) : AccountListIntent
    data class DeleteClicked(val accountId: String) : AccountListIntent
    data object DeleteConfirmed : AccountListIntent
    data object DeleteDismissed : AccountListIntent
    data object ToggleArchived : AccountListIntent
    data object MessageShown : AccountListIntent
    data object BackClicked : AccountListIntent
}

sealed interface AccountListEffect {
    /** `accountId == null` là tạo ví mới. */
    data class OpenEditor(val accountId: String?) : AccountListEffect
    data object NavigateBack : AccountListEffect
}
