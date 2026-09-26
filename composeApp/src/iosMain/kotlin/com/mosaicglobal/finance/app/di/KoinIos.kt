package com.mosaicglobal.finance.app.di

import com.mosaicglobal.finance.core.sync.scheduler.SyncScheduler
import kotlin.experimental.ExperimentalNativeApi

/**
 * Được `iOSApp.swift` gọi (`KoinIosKt.startKoinIos()`) trước khi tạo view đầu tiên. Resolve
 * [SyncScheduler] ở đây để đăng ký launch handler của BGTaskScheduler — Apple bắt buộc việc này xảy ra
 * trước khi app launch xong.
 */
@Suppress("unused")
fun startKoinIos() {
    val koin = initKoin().koin
    koin.get<SyncScheduler>().schedulePeriodic()
}

actual fun defaultApiBaseUrl(): String =
    if (isDebugBuild) "http://localhost:8080" else "https://api.finance.example.com"

/**
 * ⚠️ Local dev trên iOS simulator CHƯA chạy được: Keycloak cố định `KC_HOSTNAME=10.0.2.2` cho Android
 * emulator, nên discovery trả issuer `10.0.2.2` ≠ `localhost` và sign-in bị reject (đúng thiết kế).
 * Cần một hostname mà cả emulator lẫn simulator cùng tới được — xem Phase 6 của plan.
 */
actual fun defaultOidcIssuer(): String =
    if (isDebugBuild) "http://localhost:8081/realms/finance" else "https://login.finance.example.com/realms/finance"

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = kotlin.native.Platform.isDebugBinary
