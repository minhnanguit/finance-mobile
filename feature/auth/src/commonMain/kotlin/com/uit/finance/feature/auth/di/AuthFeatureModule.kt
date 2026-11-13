package com.uit.finance.feature.auth.di

import com.uit.finance.feature.auth.data.remote.UserRemoteDataSource
import com.uit.finance.feature.auth.data.repository.AuthRepositoryImpl
import com.uit.finance.feature.auth.domain.repository.AuthRepository
import com.uit.finance.feature.auth.domain.usecase.AcknowledgeSignOutNoticeUseCase
import com.uit.finance.feature.auth.domain.usecase.CheckLocalDataOnDeviceUseCase
import com.uit.finance.feature.auth.domain.usecase.CheckOtherAccountsDataUseCase
import com.uit.finance.feature.auth.domain.usecase.ObserveLocalDataStatusUseCase
import com.uit.finance.feature.auth.domain.usecase.ObserveSessionUseCase
import com.uit.finance.feature.auth.domain.usecase.ObserveSignOutNoticeUseCase
import com.uit.finance.feature.auth.domain.usecase.RetryLocalDataUseCase
import com.uit.finance.feature.auth.domain.usecase.SignInUseCase
import com.uit.finance.feature.auth.domain.usecase.WipeLocalDataUseCase
import com.uit.finance.feature.auth.domain.usecase.WipeOtherAccountsDataUseCase
import com.uit.finance.feature.auth.presentation.localdata.LocalDataGateViewModel
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
    factoryOf(::ObserveSessionUseCase)
    factoryOf(::ObserveSignOutNoticeUseCase)
    factoryOf(::AcknowledgeSignOutNoticeUseCase)
    factoryOf(::CheckOtherAccountsDataUseCase)
    factoryOf(::WipeOtherAccountsDataUseCase)
    factoryOf(::CheckLocalDataOnDeviceUseCase)
    factoryOf(::WipeLocalDataUseCase)
    factoryOf(::ObserveLocalDataStatusUseCase)
    factoryOf(::RetryLocalDataUseCase)

    // presentation
    viewModelOf(::SignedOutViewModel)
    viewModelOf(::LocalDataGateViewModel)
}
