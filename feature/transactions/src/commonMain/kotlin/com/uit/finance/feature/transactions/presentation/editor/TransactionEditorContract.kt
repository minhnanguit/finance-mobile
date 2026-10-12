package com.uit.finance.feature.transactions.presentation.editor

import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.presentation.text.UiText
import kotlinx.datetime.LocalDate

/** Tham số mở màn (qua Koin `parametersOf`). `transactionId == null` là thêm mới. */
data class TransactionEditorArgs(val transactionId: String?)

data class TransactionEditorState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    /** Chuỗi đã làm sạch bằng `MoneyInput.sanitize` theo [currency]. */
    val amount: String = "",
    /** Phí chuyển (chỉ chuyển tiền mới). */
    val fee: String = "",
    val currency: String = "VND",
    val accountId: String? = null,
    val counterAccountId: String? = null,
    val categoryId: String? = null,
    val occurredOn: LocalDate = LocalDate(2000, 1, 1),
    val dateLabel: String = "",
    val dateRange: ClosedRange<LocalDate> = LocalDate(2000, 1, 1)..LocalDate(2000, 1, 1),
    val payee: String = "",
    val note: String = "",
    /** Ví chọn được làm ví đi (đang dùng, cộng ví hiện tại của giao dịch đang sửa). */
    val accounts: List<AccountOptionUi> = emptyList(),
    /** Ví nhận chọn được: khác ví đi, cùng tiền tệ. */
    val counterAccounts: List<AccountOptionUi> = emptyList(),
    /** Danh mục đang dùng đúng loại thu/chi của [type]. */
    val categories: List<CategoryOptionUi> = emptyList(),
    val picker: EditorPicker? = null,
    val isSaving: Boolean = false,
    val confirmDelete: Boolean = false,
    val amountError: UiText? = null,
    val feeError: UiText? = null,
    val categoryError: UiText? = null,
    val accountError: UiText? = null,
    val counterAccountError: UiText? = null,
    val dateError: UiText? = null,
    val payeeError: UiText? = null,
    val noteError: UiText? = null,
    val error: UiText? = null,
) {
    val isTransfer: Boolean get() = type == TransactionType.TRANSFER
    val hasAccounts: Boolean get() = accounts.isNotEmpty()
    val selectedAccount: AccountOptionUi? get() = accounts.firstOrNull { it.id == accountId }
    val selectedCounterAccount: AccountOptionUi? get() = counterAccounts.firstOrNull { it.id == counterAccountId }

    val canSave: Boolean
        get() = !isLoading && !isSaving && amount.isNotEmpty() && accountId != null &&
            if (isTransfer) counterAccountId != null else categoryId != null
}

data class AccountOptionUi(val id: String, val name: String, val type: AccountType, val currency: String)

data class CategoryOptionUi(val id: String, val name: String, val kind: CategoryKind, val iconKey: String?, val colorHex: String?)

enum class EditorPicker { Account, CounterAccount, Date }

sealed interface TransactionEditorIntent {
    data class TypeChanged(val value: TransactionType) : TransactionEditorIntent
    data class AmountChanged(val value: String) : TransactionEditorIntent
    data class FeeChanged(val value: String) : TransactionEditorIntent
    data class CategorySelected(val categoryId: String) : TransactionEditorIntent
    data class PickerOpened(val picker: EditorPicker) : TransactionEditorIntent
    data object PickerDismissed : TransactionEditorIntent
    data class AccountSelected(val accountId: String) : TransactionEditorIntent
    data class CounterAccountSelected(val accountId: String) : TransactionEditorIntent
    data class DateSelected(val date: LocalDate) : TransactionEditorIntent
    data class PayeeChanged(val value: String) : TransactionEditorIntent
    data class NoteChanged(val value: String) : TransactionEditorIntent
    data object SaveClicked : TransactionEditorIntent
    data object DeleteClicked : TransactionEditorIntent
    data object DeleteConfirmed : TransactionEditorIntent
    data object DeleteDismissed : TransactionEditorIntent
    data object ErrorDismissed : TransactionEditorIntent
    data object CreateAccountClicked : TransactionEditorIntent
    data object ManageCategoriesClicked : TransactionEditorIntent
    data object BackClicked : TransactionEditorIntent
}

sealed interface TransactionEditorEffect {
    data object Close : TransactionEditorEffect
    data object OpenCreateAccount : TransactionEditorEffect
    data class OpenCategories(val kind: CategoryKind) : TransactionEditorEffect
}
