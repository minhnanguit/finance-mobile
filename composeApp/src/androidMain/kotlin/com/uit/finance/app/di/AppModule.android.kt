package com.uit.finance.app.di

import com.uit.finance.BuildConfig
import com.uit.finance.app.config.AppEnvironment

// Debug: ưu tiên URL HTTPS của tunnel (một host cho cả API lẫn Keycloak, Caddy chia theo path);
// không có thì dùng 10.0.2.2 — host loopback nhìn từ Android emulator.

actual fun defaultApiBaseUrl(): String =
    if (BuildConfig.DEBUG) AppEnvironment.publicBaseUrl ?: "http://10.0.2.2:8080" else RELEASE_API_BASE_URL

actual fun defaultOidcIssuer(): String =
    if (BuildConfig.DEBUG) (AppEnvironment.publicBaseUrl ?: "http://10.0.2.2:8081") + OIDC_REALM_PATH else RELEASE_OIDC_ISSUER

actual val isDebugBuild: Boolean = BuildConfig.DEBUG
