package com.uit.finance.core.presentation.di

import com.uit.finance.core.presentation.money.MoneyFormatter
import org.koin.dsl.module

/** Tiện ích hiển thị dùng chung cho mọi feature (format tiền). Đổi quy ước format = đổi ở đây. */
val corePresentationModule = module {
    single { MoneyFormatter() }
}
