package com.uit.finance.feature.accounts.di

import com.uit.finance.feature.accounts.domain.usecase.DeleteAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.MoveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.ObserveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.ObserveAccountsOverviewUseCase
import com.uit.finance.feature.accounts.domain.usecase.SaveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.SetAccountArchivedUseCase
import com.uit.finance.feature.accounts.presentation.editor.AccountEditorViewModel
import com.uit.finance.feature.accounts.presentation.list.AccountListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Cần trong graph: repository của `coreLedgerModule`, `MoneyFormatter` (core/presentation). */
val accountsFeatureModule: Module = module {
    // domain
    factoryOf(::ObserveAccountsOverviewUseCase)
    factoryOf(::ObserveAccountUseCase)
    factoryOf(::SaveAccountUseCase)
    factoryOf(::SetAccountArchivedUseCase)
    factoryOf(::DeleteAccountUseCase)
    factoryOf(::MoveAccountUseCase)

    // presentation
    viewModelOf(::AccountListViewModel)
    viewModel { params -> AccountEditorViewModel(args = params.get(), observeAccount = get(), saveAccount = get()) }
}
