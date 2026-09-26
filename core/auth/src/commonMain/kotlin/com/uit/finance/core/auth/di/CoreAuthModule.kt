package com.uit.finance.core.auth.di

import com.uit.finance.core.auth.OidcAuthenticator
import com.uit.finance.core.auth.client.OidcClient
import com.uit.finance.core.auth.client.createOidcHttpClient
import com.uit.finance.core.auth.flow.DefaultOidcAuthenticator
import com.uit.finance.core.auth.pkce.PkceGenerator
import com.uit.finance.core.auth.refresh.OidcTokenRefresher
import com.uit.finance.core.network.auth.TokenRefresher
import org.koin.core.module.Module
import org.koin.dsl.module

/** Cung cấp `AuthorizationLauncher` theo nền tảng. */
internal expect fun platformAuthModule(): Module

/**
 * Cần có trong graph: `OidcConfig` (app), `HttpClientEngine` (core/network), `DispatcherProvider` +
 * Kermit `Logger` (core/common).
 */
val coreAuthModule: Module = module {
    includes(platformAuthModule())
    single { OidcClient(http = createOidcHttpClient(engine = get()), config = get()) }
    single { PkceGenerator() }
    single<OidcAuthenticator> {
        DefaultOidcAuthenticator(client = get(), launcher = get(), pkceGenerator = get(), config = get(), logger = get())
    }
    single<TokenRefresher> { OidcTokenRefresher(client = get(), logger = get()) }
}
