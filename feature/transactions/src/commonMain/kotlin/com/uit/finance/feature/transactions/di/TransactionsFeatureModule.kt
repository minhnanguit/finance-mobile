package com.uit.finance.feature.transactions.di

import com.uit.finance.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveCategoriesUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionFormDataUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionHistoryUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.SaveCategoryUseCase
import com.uit.finance.feature.transactions.domain.usecase.SaveTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.SetCategoryArchivedUseCase
import com.uit.finance.feature.transactions.presentation.categories.CategoriesViewModel
import com.uit.finance.feature.transactions.presentation.categoryeditor.CategoryEditorViewModel
import com.uit.finance.feature.transactions.presentation.editor.TransactionEditorViewModel
import com.uit.finance.feature.transactions.presentation.history.HistoryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Cần trong graph: repository của `coreLedgerModule`, use case sync của `coreSyncModule`, `Clock` (core/common),
 * `TransactionItemFormatter` (core/ui).
 */
val transactionsFeatureModule: Module = module {
    // domain
    factoryOf(::ObserveTransactionHistoryUseCase)
    factoryOf(::ObserveTransactionFormDataUseCase)
    factoryOf(::ObserveTransactionUseCase)
    factoryOf(::SaveTransactionUseCase)
    factoryOf(::DeleteTransactionUseCase)
    factoryOf(::ObserveCategoriesUseCase)
    factoryOf(::SaveCategoryUseCase)
    factoryOf(::SetCategoryArchivedUseCase)

    // presentation
    viewModelOf(::HistoryViewModel)
    viewModel { params ->
        TransactionEditorViewModel(
            args = params.get(),
            observeFormData = get(),
            observeTransaction = get(),
            saveTransaction = get(),
            deleteTransaction = get(),
            clock = get(),
        )
    }
    viewModel { params -> CategoriesViewModel(args = params.get(), observeCategories = get(), setArchived = get()) }
    viewModel { params -> CategoryEditorViewModel(args = params.get(), observeCategories = get(), saveCategory = get()) }
}
