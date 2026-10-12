package com.uit.finance.feature.accounts.presentation.editor

import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.presentation.text.UiText

/** Tham số mở màn (qua Koin `parametersOf`). `accountId == null` là tạo ví mới. */
data class AccountEditorArgs(val accountId: String?)

data class AccountEditorState(
    val isLoading: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.CASH,
    val currency: String = DEFAULT_CURRENCY,
    /** Chuỗi đã làm sạch bằng `MoneyInput.sanitize`, có thể có dấu "-" (ví đang nợ). */
    val openingBalance: String = "",
    val currencyPickerVisible: Boolean = false,
    val isSaving: Boolean = false,
    val nameError: UiText? = null,
    val currencyError: UiText? = null,
    val openingBalanceError: UiText? = null,
    val error: UiText? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving && !isLoading

    companion object {
        const val DEFAULT_CURRENCY = "VND"
    }
}

sealed interface AccountEditorIntent {
    data class NameChanged(val value: String) : AccountEditorIntent
    data class TypeChanged(val value: AccountType) : AccountEditorIntent
    data class OpeningBalanceChanged(val value: String) : AccountEditorIntent
    data object CurrencyClicked : AccountEditorIntent
    data class CurrencySelected(val code: String) : AccountEditorIntent
    data object CurrencyPickerDismissed : AccountEditorIntent
    data object SaveClicked : AccountEditorIntent
    data object ErrorDismissed : AccountEditorIntent
    data object BackClicked : AccountEditorIntent
}

sealed interface AccountEditorEffect {
    /** Lưu xong hoặc user quay lại. */
    data object Close : AccountEditorEffect
}
