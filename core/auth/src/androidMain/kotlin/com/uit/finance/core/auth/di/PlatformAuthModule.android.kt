package com.uit.finance.core.auth.di

import com.uit.finance.core.auth.launcher.AuthorizationLauncher
import com.uit.finance.core.auth.launcher.CustomTabsAuthorizationLauncher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformAuthModule(): Module = module {
    single<AuthorizationLauncher> { CustomTabsAuthorizationLauncher(androidContext()) }
}
