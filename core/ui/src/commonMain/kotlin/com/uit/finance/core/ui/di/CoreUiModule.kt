package com.uit.finance.core.ui.di

import com.uit.finance.core.ui.transaction.TransactionItemFormatter
import org.koin.dsl.module

/** Cần `MoneyFormatter` (corePresentationModule). */
val coreUiModule = module {
    factory { TransactionItemFormatter(money = get()) }
}
