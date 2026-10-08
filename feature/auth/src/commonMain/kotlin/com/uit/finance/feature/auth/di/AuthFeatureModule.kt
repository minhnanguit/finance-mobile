package com.uit.finance.feature.auth.di

import com.uit.finance.feature.auth.data.remote.UserRemoteDataSource
import com.uit.finance.feature.auth.data.repository.AuthRepositoryImpl
import com.uit.finance.feature.auth.domain.repository.AuthRepository
import com.uit.finance.feature.auth.domain.usecase.CheckOtherAccountsDataUseCase
import com.uit.finance.feature.auth.domain.usecase.CountUnsentChangesUseCase
import com.uit.finance.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.uit.finance.feature.auth.domain.usecase.LogoutUseCase
import com.uit.finance.feature.auth.domain.usecase.ObserveSessionUseCase
import com.uit.finance.feature.auth.domain.usecase.SignInUseCase
import com.uit.finance.feature.auth.domain.usecase.WipeOtherAccountsDataUseCase
import com.uit.finance.feature.auth.presentation.profile.ProfileViewModel
import com.uit.finance.feature.auth.presentation.signedout.SignedOutViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authFeatureModule: Module = module {
    // data
    singleOf(::UserRemoteDataSource)
    singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }

    // domain
    factoryOf(::SignInUseCase)
    factoryOf(::LogoutUseCase)
    factoryOf(::ObserveSessionUseCase)
    factoryOf(::GetCurrentUserUseCase)
    factoryOf(::CountUnsentChangesUseCase)
    factoryOf(::CheckOtherAccountsDataUseCase)
    factoryOf(::WipeOtherAccountsDataUseCase)

    // presentation
    viewModelOf(::SignedOutViewModel)
    viewModelOf(::ProfileViewModel)
}
