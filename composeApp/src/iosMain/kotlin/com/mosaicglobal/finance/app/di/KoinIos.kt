package com.mosaicglobal.finance.app.di

import com.mosaicglobal.finance.core.sync.scheduler.SyncScheduler
import kotlin.experimental.ExperimentalNativeApi

/**
 * Called from `iOSApp.swift` (`KoinIosKt.startKoinIos()`) before the first view is created.
 * Resolving [SyncScheduler] here registers the BGTaskScheduler launch handler, which Apple requires
 * to happen before the app finishes launching.
 */
@Suppress("unused")
fun startKoinIos() {
    val koin = initKoin().koin
    koin.get<SyncScheduler>().schedulePeriodic()
}

actual fun defaultApiBaseUrl(): String =
    if (isDebugBuild) "http://localhost:8080" else "https://api.finance.example.com"

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = kotlin.native.Platform.isDebugBinary
