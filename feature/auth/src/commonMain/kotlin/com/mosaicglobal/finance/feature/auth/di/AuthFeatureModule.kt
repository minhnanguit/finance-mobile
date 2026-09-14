package com.mosaicglobal.finance.feature.auth.di

import com.mosaicglobal.finance.feature.auth.data.device.DefaultDeviceInfoProvider
import com.mosaicglobal.finance.feature.auth.data.remote.AuthRemoteDataSource
import com.mosaicglobal.finance.feature.auth.data.repository.AuthRepositoryImpl
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository
import com.mosaicglobal.finance.feature.auth.domain.repository.DeviceInfoProvider
import com.mosaicglobal.finance.feature.auth.domain.usecase.GetCurrentUserUseCase
import com.mosaicglobal.finance.feature.auth.domain.usecase.LoginUseCase
import com.mosaicglobal.finance.feature.auth.domain.usecase.LogoutUseCase
import com.mosaicglobal.finance.feature.auth.domain.usecase.ObserveSessionUseCase
import com.mosaicglobal.finance.feature.auth.domain.usecase.RegisterUseCase
import com.mosaicglobal.finance.feature.auth.presentation.login.LoginViewModel
import com.mosaicglobal.finance.feature.auth.presentation.profile.ProfileViewModel
import com.mosaicglobal.finance.feature.auth.presentation.register.RegisterViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authFeatureModule: Module = module {
    // data
    singleOf(::AuthRemoteDataSource)
    singleOf(::DefaultDeviceInfoProvider) { bind<DeviceInfoProvider>() }
    singleOf(::AuthRepositoryImpl) { bind<AuthRepository>() }

    // domain
    factoryOf(::LoginUseCase)
    factoryOf(::RegisterUseCase)
    factoryOf(::LogoutUseCase)
    factoryOf(::ObserveSessionUseCase)
    factoryOf(::GetCurrentUserUseCase)

    // presentation
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::ProfileViewModel)
}
