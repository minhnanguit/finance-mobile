package com.uit.finance.feature.transactions.presentation.categoryeditor

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.ui.ledger.messageFor
import com.uit.finance.core.ui.ledger.toLedgerUiText
import com.uit.finance.feature.transactions.domain.model.CategoryInput
import com.uit.finance.feature.transactions.domain.usecase.ObserveCategoriesUseCase
import com.uit.finance.feature.transactions.domain.usecase.SaveCategoryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class CategoryEditorViewModel(
    private val args: CategoryEditorArgs,
    private val observeCategories: ObserveCategoriesUseCase,
    private val saveCategory: SaveCategoryUseCase,
) : MviViewModel<CategoryEditorState, CategoryEditorIntent, CategoryEditorEffect>(
    CategoryEditorState(isLoading = args.categoryId != null, isNew = args.categoryId == null, kind = args.kind),
) {

    init {
        args.categoryId?.let(::load)
    }

    override fun onIntent(intent: CategoryEditorIntent) = when (intent) {
        is CategoryEditorIntent.NameChanged -> setState { copy(name = intent.value, nameError = null) }
        // Loại thu/chi chỉ chọn được lúc tạo (`ledger.kind_immutable`).
        is CategoryEditorIntent.KindChanged -> if (currentState.isNew) setState { copy(kind = intent.value) } else Unit
        is CategoryEditorIntent.IconSelected -> setState { copy(iconKey = intent.key) }
        is CategoryEditorIntent.ColorSelected -> setState { copy(colorHex = intent.hex) }
        CategoryEditorIntent.SaveClicked -> save()
        CategoryEditorIntent.ErrorDismissed -> setState { copy(error = null) }
        CategoryEditorIntent.BackClicked -> sendEffect(CategoryEditorEffect.Close)
    }

    private fun load(categoryId: String) {
        viewModelScope.launch {
            val category = observeCategories().first().firstOrNull { it.id == categoryId }
            if (category == null) {
                sendEffect(CategoryEditorEffect.Close)
                return@launch
            }
            setState {
                copy(isLoading = false, kind = category.kind, name = category.name, iconKey = category.icon, colorHex = category.color)
            }
        }
    }

    private fun save() {
        val state = currentState
        if (!state.canSave) return
        setState { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            saveCategory(CategoryInput(args.categoryId, state.kind, state.name, state.iconKey, state.colorHex)).fold(
                onSuccess = { sendEffect(CategoryEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false).withError(error) } },
            )
        }
    }

    private fun CategoryEditorState.withError(error: AppError): CategoryEditorState {
        val nameError = (error as? AppError.Validation)?.fieldErrors.orEmpty().messageFor("name")
        return copy(nameError = nameError, error = if (nameError == null) error.toLedgerUiText() else null)
    }
}
