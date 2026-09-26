package com.mosaicglobal.finance.app.di

import com.mosaicglobal.finance.core.auth.OidcConfig
import com.mosaicglobal.finance.core.network.client.NetworkConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/** Backend của build này: loopback của emulator/simulator khi debug, host thật khi release. */
expect fun defaultApiBaseUrl(): String

/** Issuer của Keycloak, phải trùng TUYỆT ĐỐI claim `iss` trong token (xem `KC_HOSTNAME`). */
expect fun defaultOidcIssuer(): String

expect val isDebugBuild: Boolean

/**
 * Phải khớp cả ba nơi: `redirectUris` của client `finance-mobile` trong `realm-finance.json`,
 * intent-filter của `RedirectReceiverActivity` trong AndroidManifest, và scheme mà iOS dùng.
 */
internal const val OIDC_REDIRECT_URI = "com.mosaicglobal.finance://oauth/callback"
internal const val OIDC_CLIENT_ID = "finance-mobile"

internal val appModule: Module = module {
    single { NetworkConfig(baseUrl = defaultApiBaseUrl(), logHttp = isDebugBuild) }
    single { OidcConfig(issuer = defaultOidcIssuer(), clientId = OIDC_CLIENT_ID, redirectUri = OIDC_REDIRECT_URI) }
}
