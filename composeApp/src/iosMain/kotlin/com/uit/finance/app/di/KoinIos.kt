package com.uit.finance.app.di

import com.uit.finance.app.config.AppEnvironment
import com.uit.finance.core.sync.scheduler.SyncScheduler
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
    if (isDebugBuild) AppEnvironment.publicBaseUrl ?: "http://localhost:8080" else RELEASE_API_BASE_URL

/**
 * Simulator chỉ login được khi có tunnel (`make tunnel URL=...`): Keycloak local mặc định phát issuer
 * `10.0.2.2` cho Android emulator, còn simulator gọi `localhost` ⇒ issuer lệch, bị reject (đúng thiết kế).
 */
actual fun defaultOidcIssuer(): String =
    if (isDebugBuild) (AppEnvironment.publicBaseUrl ?: "http://localhost:8081") + OIDC_REALM_PATH else RELEASE_OIDC_ISSUER

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = kotlin.native.Platform.isDebugBinary
