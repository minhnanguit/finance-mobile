package com.uit.finance.app.di

import com.uit.finance.app.devtools.DemoDataSeeder
import com.uit.finance.core.auth.OidcConfig
import com.uit.finance.core.auth.preferredUiLocales
import com.uit.finance.core.network.client.NetworkConfig
import com.uit.finance.feature.settings.domain.model.SettingsAppInfo
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** Backend của build này: loopback của emulator/simulator khi debug, host thật khi release. */
expect fun defaultApiBaseUrl(): String

/** Issuer của Keycloak, phải trùng TUYỆT ĐỐI claim `iss` trong token (xem `KC_HOSTNAME`). */
expect fun defaultOidcIssuer(): String

/** ISO language code selected by the operating system for the app. */
expect fun currentLanguageCode(): String

expect val isDebugBuild: Boolean

/** Phiên bản hiển thị ở Cài đặt: `versionName` (Android) / `CFBundleShortVersionString` (iOS). */
expect val appVersionName: String

/**
 * Phải khớp cả ba nơi: `redirectUris` của client `finance-mobile` trong `realm-finance.json`,
 * intent-filter của `RedirectReceiverActivity` trong AndroidManifest, và scheme mà iOS dùng.
 */
internal const val OIDC_REALM_PATH = "/realms/finance"
internal const val RELEASE_API_BASE_URL = "https://api.finance.example.com"
internal const val RELEASE_OIDC_ISSUER = "https://login.finance.example.com$OIDC_REALM_PATH"

internal const val OIDC_REDIRECT_URI = "com.uit.finance://oauth/callback"
internal const val OIDC_CLIENT_ID = "finance-mobile"

/** Chính sách quyền riêng tư của bản phát hành (App Store 5.1.1(i) và Google Play đều đòi xem được trong app, ADR-008 C3). */
internal const val PRIVACY_POLICY_URL = "https://finance.example.com/privacy"

internal val appModule: Module = module {
    single { NetworkConfig(baseUrl = defaultApiBaseUrl(), logHttp = isDebugBuild) }
    single {
        OidcConfig(
            issuer = defaultOidcIssuer(),
            clientId = OIDC_CLIENT_ID,
            redirectUri = OIDC_REDIRECT_URI,
            uiLocales = preferredUiLocales(currentLanguageCode()),
        )
    }
    single { SettingsAppInfo(versionName = appVersionName, privacyPolicyUrl = PRIVACY_POLICY_URL) }

    // Công cụ dev (menu DEV, dữ liệu mẫu): bản release không đăng ký nên không resolve được.
    if (isDebugBuild) factoryOf(::DemoDataSeeder)
}
