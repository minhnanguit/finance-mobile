package com.uit.finance.feature.reports.di

import com.uit.finance.feature.reports.domain.usecase.ObserveCategoryBreakdownUseCase
import com.uit.finance.feature.reports.domain.usecase.ObserveReportOverviewUseCase
import com.uit.finance.feature.reports.domain.usecase.ObserveTodayUseCase
import com.uit.finance.feature.reports.presentation.breakdown.CategoryBreakdownViewModel
import com.uit.finance.feature.reports.presentation.common.ReportFormatter
import com.uit.finance.feature.reports.presentation.overview.ReportsUiMapper
import com.uit.finance.feature.reports.presentation.overview.ReportsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Cần trong graph: repository của `coreLedgerModule` (gồm `SpendingRepository`), `Clock`, `MoneyFormatter`. */
val reportsFeatureModule: Module = module {
    // domain
    factoryOf(::ObserveReportOverviewUseCase)
    factoryOf(::ObserveCategoryBreakdownUseCase)
    factory { ObserveTodayUseCase(clock = get()) }

    // presentation
    factoryOf(::ReportFormatter)
    factoryOf(::ReportsUiMapper)
    viewModelOf(::ReportsViewModel)
    viewModel { params -> CategoryBreakdownViewModel(args = params.get(), observeBreakdown = get(), format = get()) }
}
