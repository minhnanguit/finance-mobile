package com.mosaicglobal.finance.app.di

import com.mosaicglobal.finance.BuildConfig

/** 10.0.2.2 là host loopback nhìn từ Android emulator. */
actual fun defaultApiBaseUrl(): String =
    if (BuildConfig.DEBUG) "http://10.0.2.2:8080" else "https://api.finance.example.com"

actual fun defaultOidcIssuer(): String =
    if (BuildConfig.DEBUG) "http://10.0.2.2:8081/realms/finance" else "https://login.finance.example.com/realms/finance"

actual val isDebugBuild: Boolean = BuildConfig.DEBUG
