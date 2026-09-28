package com.uit.finance.core.auth.launcher

/**
 * Mở authorization URL bằng system browser của nền tảng và chờ redirect quay về.
 * Android: Custom Tabs. iOS: ASWebAuthenticationSession. Không bao giờ dùng WebView (RFC 8252).
 */
internal interface AuthorizationLauncher {
    suspend fun launch(authorizationUrl: String, redirectUri: String): LaunchResult
}

internal sealed interface LaunchResult {
    data class Redirected(val callbackUri: String) : LaunchResult
    data object Cancelled : LaunchResult
    data class Failed(val reason: String?) : LaunchResult
}
