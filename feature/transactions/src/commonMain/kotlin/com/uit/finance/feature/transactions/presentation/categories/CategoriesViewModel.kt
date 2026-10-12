package com.uit.finance.feature.transactions.presentation.categories

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.ui.ledger.toLedgerUiText
import com.uit.finance.feature.transactions.domain.usecase.ObserveCategoriesUseCase
import com.uit.finance.feature.transactions.domain.usecase.SetCategoryArchivedUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class CategoriesViewModel(
    args: CategoriesArgs,
    observeCategories: ObserveCategoriesUseCase,
    private val setArchived: SetCategoryArchivedUseCase,
) : MviViewModel<CategoriesState, CategoriesIntent, CategoriesEffect>(CategoriesState(kind = args.kind)) {

    private var all: List<Category> = emptyList()

    init {
        observeCategories()
            .onEach { categories ->
                all = categories
                setState { rebuild() }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: CategoriesIntent) = when (intent) {
        is CategoriesIntent.KindChanged -> setState { copy(kind = intent.kind).rebuild() }
        CategoriesIntent.AddClicked -> sendEffect(CategoriesEffect.OpenEditor(null, currentState.kind))
        is CategoriesIntent.EditClicked -> sendEffect(CategoriesEffect.OpenEditor(intent.categoryId, currentState.kind))
        is CategoriesIntent.ArchiveClicked -> archive(intent.categoryId, intent.archived)
        CategoriesIntent.ToggleArchived -> setState { copy(showArchived = !showArchived) }
        CategoriesIntent.MessageShown -> setState { copy(message = null) }
        CategoriesIntent.BackClicked -> sendEffect(CategoriesEffect.NavigateBack)
    }

    private fun archive(categoryId: String, archived: Boolean) {
        viewModelScope.launch {
            setArchived(categoryId, archived).fold(
                onSuccess = {
                    if (archived) setState { copy(message = UiText.Key("categories.archived", "Đã lưu trữ danh mục.")) }
                },
                onFailure = { error -> setState { copy(message = error.toLedgerUiText()) } },
            )
        }
    }

    private fun CategoriesState.rebuild(): CategoriesState {
        val (archived, active) = all
            .filter { it.kind == kind }
            .sortedBy { it.name.lowercase() }
            .map { CategoryItemUi(it.id, it.name, it.kind, it.icon, it.color, it.archived, isDefault = it.templateKey != null) }
            .partition { it.archived }
        return copy(isLoading = false, active = active, archived = archived)
    }
}
