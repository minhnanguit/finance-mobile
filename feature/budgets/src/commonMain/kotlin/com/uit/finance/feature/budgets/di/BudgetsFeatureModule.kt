package com.uit.finance.feature.budgets.di

import com.uit.finance.feature.budgets.domain.usecase.DeleteBudgetUseCase
import com.uit.finance.feature.budgets.domain.usecase.ObserveBudgetFormDataUseCase
import com.uit.finance.feature.budgets.domain.usecase.ObserveBudgetProgressUseCase
import com.uit.finance.feature.budgets.domain.usecase.ObserveTodayUseCase
import com.uit.finance.feature.budgets.domain.usecase.SaveBudgetUseCase
import com.uit.finance.feature.budgets.presentation.editor.BudgetEditorViewModel
import com.uit.finance.feature.budgets.presentation.list.BudgetListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Cần trong graph: repository của `coreBudgetModule` + `coreLedgerModule`, use case sync của `coreSyncModule`,
 * `MoneyFormatter` (core/presentation), `Clock` (core/common).
 */
val budgetsFeatureModule: Module = module {
    // domain
    factoryOf(::ObserveBudgetProgressUseCase)
    factoryOf(::ObserveBudgetFormDataUseCase)
    factoryOf(::SaveBudgetUseCase)
    factoryOf(::DeleteBudgetUseCase)
    factory { ObserveTodayUseCase(clock = get()) }

    // presentation
    viewModelOf(::BudgetListViewModel)
    viewModel { params ->
        BudgetEditorViewModel(
            args = params.get(),
            observeFormData = get(),
            saveBudget = get(),
            deleteBudget = get(),
            clock = get(),
        )
    }
}
