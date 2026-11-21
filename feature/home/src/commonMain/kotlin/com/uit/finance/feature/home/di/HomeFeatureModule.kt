package com.uit.finance.feature.home.di

import com.uit.finance.feature.home.domain.usecase.ObserveHomeDashboardUseCase
import com.uit.finance.feature.home.domain.usecase.ObservePendingReviewCountUseCase
import com.uit.finance.feature.home.presentation.home.HomeUiMapper
import com.uit.finance.feature.home.presentation.home.HomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Cần trong graph: 4 repository của `coreLedgerModule`, `CaptureRepository` (core/capture), use case sync của `coreSyncModule`, `Clock`
 * (core/common), `MoneyFormatter` (core/presentation), `TransactionItemFormatter` (core/ui).
 */
val homeFeatureModule: Module = module {
    // domain
    factoryOf(::ObserveHomeDashboardUseCase)
    factoryOf(::ObservePendingReviewCountUseCase)

    // presentation
    factoryOf(::HomeUiMapper)
    viewModelOf(::HomeViewModel)
}
