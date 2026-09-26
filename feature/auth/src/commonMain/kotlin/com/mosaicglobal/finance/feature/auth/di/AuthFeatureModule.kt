package com.mosaicglobal.finance.feature.auth.di

import com.mosaicglobal.finance.feature.auth.data.remote.UserRemoteDataSource
import com.mosaicglobal.finance.feature.auth.data.repository.AuthRepositoryImpl
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository
import com.mosaicglobal.finance.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.mosaicglobal.finance.feature.auth.domain.usecase.LogoutUseCase
import com.mosaicglobal.finance.feature.auth.domain.usecase.ObserveSessionUseCase
import com.mosaicglobal.finance.feature.auth.domain.usecase.SignInUseCase
import com.mosaicglobal.finance.feature.auth.presentation.profile.ProfileViewModel
import com.mosaicglobal.finance.feature.auth.presentation.signedout.SignedOutViewModel
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

    // presentation
    viewModelOf(::SignedOutViewModel)
    viewModelOf(::ProfileViewModel)
}
