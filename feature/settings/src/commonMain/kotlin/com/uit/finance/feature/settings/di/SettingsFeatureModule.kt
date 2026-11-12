package com.uit.finance.feature.settings.di

import com.uit.finance.feature.settings.data.remote.AccountRemoteDataSource
import com.uit.finance.feature.settings.data.repository.AccountRepositoryImpl
import com.uit.finance.feature.settings.domain.repository.AccountRepository
import com.uit.finance.feature.settings.domain.usecase.ChangePasswordUseCase
import com.uit.finance.feature.settings.domain.usecase.CountUnsentChangesUseCase
import com.uit.finance.feature.settings.domain.usecase.DeleteAccountUseCase
import com.uit.finance.feature.settings.domain.usecase.LogoutUseCase
import com.uit.finance.feature.settings.domain.usecase.ObserveAccountProfileUseCase
import com.uit.finance.feature.settings.presentation.deleteaccount.DeleteAccountViewModel
import com.uit.finance.feature.settings.presentation.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Cần có trong graph: `SettingsAppInfo` (composeApp: version, link chính sách), `OidcAuthenticator` (core/auth),
 * `UserApi` + `TokenCache` (core/network), `SessionStore` + `TokenProvider` (core/datastore), `UserSession`
 * (core/session), `OutboxRepository` (core/sync).
 */
val settingsFeatureModule: Module = module {
    // data
    singleOf(::AccountRemoteDataSource)
    singleOf(::AccountRepositoryImpl) { bind<AccountRepository>() }

    // domain
    factoryOf(::ObserveAccountProfileUseCase)
    factoryOf(::ChangePasswordUseCase)
    factoryOf(::CountUnsentChangesUseCase)
    factoryOf(::LogoutUseCase)
    factoryOf(::DeleteAccountUseCase)

    // presentation
    viewModelOf(::SettingsViewModel)
    viewModelOf(::DeleteAccountViewModel)
}
