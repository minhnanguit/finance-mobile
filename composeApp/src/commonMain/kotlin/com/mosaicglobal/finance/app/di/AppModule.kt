package com.mosaicglobal.finance.app.di

import com.mosaicglobal.finance.core.network.client.NetworkConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/** Where the backend lives for this build: emulator/simulator loopback in debug, real host otherwise. */
expect fun defaultApiBaseUrl(): String

expect val isDebugBuild: Boolean

internal val appModule: Module = module {
    single { NetworkConfig(baseUrl = defaultApiBaseUrl(), logHttp = isDebugBuild) }
}
