package com.uit.finance.feature.transactions.presentation.categories

import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.presentation.text.UiText

/** Tham số mở màn: tab thu/chi mở sẵn (từ form giao dịch thì theo loại đang ghi). */
data class CategoriesArgs(val kind: CategoryKind)

data class CategoriesState(
    val isLoading: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val active: List<CategoryItemUi> = emptyList(),
    val archived: List<CategoryItemUi> = emptyList(),
    val showArchived: Boolean = false,
    val message: UiText? = null,
)

data class CategoryItemUi(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String?,
    val colorHex: String?,
    val archived: Boolean,
    /** Danh mục mặc định của hệ thống (có `template_key`), ví dụ "Phí giao dịch". */
    val isDefault: Boolean,
)

sealed interface CategoriesIntent {
    data class KindChanged(val kind: CategoryKind) : CategoriesIntent
    data object AddClicked : CategoriesIntent
    data class EditClicked(val categoryId: String) : CategoriesIntent
    data class ArchiveClicked(val categoryId: String, val archived: Boolean) : CategoriesIntent
    data object ToggleArchived : CategoriesIntent
    data object MessageShown : CategoriesIntent
    data object BackClicked : CategoriesIntent
}

sealed interface CategoriesEffect {
    /** `categoryId == null` là tạo mới với loại [kind]. */
    data class OpenEditor(val categoryId: String?, val kind: CategoryKind) : CategoriesEffect
    data object NavigateBack : CategoriesEffect
}
