package com.mosaicglobal.finance.core.auth.di

import com.mosaicglobal.finance.core.auth.OidcAuthenticator
import com.mosaicglobal.finance.core.auth.client.OidcClient
import com.mosaicglobal.finance.core.auth.client.createOidcHttpClient
import com.mosaicglobal.finance.core.auth.flow.DefaultOidcAuthenticator
import com.mosaicglobal.finance.core.auth.pkce.PkceGenerator
import com.mosaicglobal.finance.core.auth.refresh.OidcTokenRefresher
import com.mosaicglobal.finance.core.network.auth.TokenRefresher
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
        DefaultOidcAuthenticator(client = get(), launcher = get(), pkceGenerator = get(), config = get())
    }
    single<TokenRefresher> { OidcTokenRefresher(client = get(), logger = get()) }
}
