package com.uit.finance.feature.transactions.presentation.categoryeditor

import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.presentation.text.UiText

/** Tham số mở màn. `categoryId == null` là tạo mới với loại [kind]. */
data class CategoryEditorArgs(val categoryId: String?, val kind: CategoryKind)

data class CategoryEditorState(
    val isLoading: Boolean = false,
    val isNew: Boolean = true,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val name: String = "",
    val iconKey: String? = null,
    val colorHex: String? = null,
    val isSaving: Boolean = false,
    val nameError: UiText? = null,
    val error: UiText? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving && !isLoading
}

sealed interface CategoryEditorIntent {
    data class NameChanged(val value: String) : CategoryEditorIntent
    data class KindChanged(val value: CategoryKind) : CategoryEditorIntent
    data class IconSelected(val key: String) : CategoryEditorIntent
    data class ColorSelected(val hex: String) : CategoryEditorIntent
    data object SaveClicked : CategoryEditorIntent
    data object ErrorDismissed : CategoryEditorIntent
    data object BackClicked : CategoryEditorIntent
}

sealed interface CategoryEditorEffect {
    data object Close : CategoryEditorEffect
}
