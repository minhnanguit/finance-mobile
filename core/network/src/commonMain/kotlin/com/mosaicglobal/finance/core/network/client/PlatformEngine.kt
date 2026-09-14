package com.mosaicglobal.finance.core.network.client

import io.ktor.client.engine.HttpClientEngine

/** OkHttp on Android, Darwin (NSURLSession) on iOS. Tests inject `MockEngine`. */
expect fun platformHttpEngine(): HttpClientEngine
