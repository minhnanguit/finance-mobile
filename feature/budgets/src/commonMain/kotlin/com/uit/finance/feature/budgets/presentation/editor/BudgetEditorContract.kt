package com.uit.finance.feature.budgets.presentation.editor

import com.uit.finance.core.presentation.text.UiText

/** Tham số mở màn (qua Koin `parametersOf`). `budgetId == null` là tạo ngân sách mới. */
data class BudgetEditorArgs(val budgetId: String?)

data class BudgetEditorState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    /** Danh mục chọn được: chi cấp 1, chưa lưu trữ, chưa có ngân sách ở [currency] (G2, G3, G15). */
    val categoryOptions: List<CategoryOptionUi> = emptyList(),
    val categoryId: String? = null,
    val categoryName: String? = null,
    val currency: String = "",
    val currencyOptions: List<String> = emptyList(),
    /** Chuỗi đã làm sạch bằng `MoneyInput.sanitize`. */
    val amount: String = "",
    val categoryPickerVisible: Boolean = false,
    val currencyPickerVisible: Boolean = false,
    val deleteConfirmVisible: Boolean = false,
    val isSaving: Boolean = false,
    /** Ngân sách bị xoá (có thể ở máy khác) trong lúc form đang mở: không lưu / xoá được nữa. */
    val isDeletedElsewhere: Boolean = false,
    val amountError: UiText? = null,
    val categoryError: UiText? = null,
    val error: UiText? = null,
) {
    /** Ô tiền tệ chỉ hiện khi tạo mới và user có ví nhiều tiền tệ (G15); khi sửa thì không đổi được (G8). */
    val canChooseCurrency: Boolean get() = isNew && currencyOptions.size > 1

    val canSave: Boolean get() = categoryId != null && amount.isNotBlank() && !isSaving && !isLoading && !isDeletedElsewhere
}

data class CategoryOptionUi(val id: String, val name: String, val icon: String?, val color: String?)

sealed interface BudgetEditorIntent {
    data object CategoryClicked : BudgetEditorIntent
    data class CategorySelected(val categoryId: String) : BudgetEditorIntent
    data object CategoryPickerDismissed : BudgetEditorIntent
    data object CurrencyClicked : BudgetEditorIntent
    data class CurrencySelected(val code: String) : BudgetEditorIntent
    data object CurrencyPickerDismissed : BudgetEditorIntent
    data class AmountChanged(val value: String) : BudgetEditorIntent
    data object SaveClicked : BudgetEditorIntent
    data object DeleteClicked : BudgetEditorIntent
    data object DeleteConfirmed : BudgetEditorIntent
    data object DeleteDismissed : BudgetEditorIntent
    data object ErrorDismissed : BudgetEditorIntent
    data object BackClicked : BudgetEditorIntent
}

sealed interface BudgetEditorEffect {
    /** Lưu/xoá xong, hoặc user quay lại, hoặc ngân sách không còn. */
    data object Close : BudgetEditorEffect
}
