package com.uit.finance.core.auth.launcher

/**
 * Mở authorization URL bằng system browser của nền tảng và chờ redirect quay về.
 * Android: Custom Tabs. iOS: ASWebAuthenticationSession. Không bao giờ dùng WebView (RFC 8252).
 */
internal interface AuthorizationLauncher {
    /**
     * @param ephemeral mở phiên trình duyệt riêng (không đọc, không ghi cookie của trình duyệt). Dùng cho luồng chạy
     * khi app **đã** đăng nhập (xác nhận lại, đổi mật khẩu): nếu dùng chung cookie, phiên app chính là phiên SSO của
     * trình duyệt, và nút "Restart login" của Keycloak (`skip_logout=false`) sẽ đăng xuất luôn phiên app. iOS luôn
     * ephemeral; Android cần Chrome 136+, trình duyệt không hỗ trợ thì bỏ qua cờ này.
     */
    suspend fun launch(authorizationUrl: String, redirectUri: String, ephemeral: Boolean = false): LaunchResult
}

internal sealed interface LaunchResult {
    data class Redirected(val callbackUri: String) : LaunchResult
    data object Cancelled : LaunchResult
    data class Failed(val reason: String?) : LaunchResult
}
