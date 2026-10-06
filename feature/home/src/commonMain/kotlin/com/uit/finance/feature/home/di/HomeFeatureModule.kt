package com.uit.finance.feature.home.di

import com.uit.finance.feature.home.presentation.home.HomeViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeFeatureModule: Module = module {
    viewModelOf(::HomeViewModel)
}
