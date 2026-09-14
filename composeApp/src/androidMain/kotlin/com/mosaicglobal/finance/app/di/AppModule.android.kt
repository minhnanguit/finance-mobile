package com.mosaicglobal.finance.app.di

import com.mosaicglobal.finance.BuildConfig

/** 10.0.2.2 is the host loopback as seen from the Android emulator. */
actual fun defaultApiBaseUrl(): String =
    if (BuildConfig.DEBUG) "http://10.0.2.2:8080" else "https://api.finance.example.com"

actual val isDebugBuild: Boolean = BuildConfig.DEBUG
