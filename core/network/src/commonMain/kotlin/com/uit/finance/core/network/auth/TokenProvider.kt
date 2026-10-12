package com.uit.finance.core.network.auth

/** Cặp token do Keycloak issue (ADR-004). */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Thời gian sống của access token (giây), theo `expires_in` của token endpoint. */
    val expiresInSeconds: Int,
)

/**
 * Port của network layer, `core/datastore` implement (`SecureSessionStore`). Ktor bearer plugin đọc
 * token ở đây và ghi token mới sau mỗi lần refresh.
 *
 * Ghi và xoá đều là **compare-and-set theo refresh token**: một lần refresh bắt đầu từ phiên cũ (user A vừa
 * đăng xuất, B vừa đăng nhập) mà về muộn thì không được đè token của B, cũng không được đăng xuất B, và
 * không được "hồi sinh" phiên của A sau khi A đã đăng xuất (ADR-006 B5).
 */
interface TokenProvider {
    suspend fun tokens(): AuthTokens?

    /**
     * Lưu [tokens] **chỉ khi** phiên hiện tại vẫn là phiên có refresh token [refreshedFrom].
     * @return `false` nếu phiên đã đổi hoặc đã bị xoá trong lúc refresh: token mới bị bỏ đi.
     */
    suspend fun replaceIfCurrent(refreshedFrom: String, tokens: AuthTokens): Boolean

    /**
     * IdP từ chối [rejectedRefreshToken]: xoá phiên **chỉ khi** phiên hiện tại vẫn dùng refresh token đó.
     * @return `false` nếu phiên đã đổi (không xoá gì).
     */
    suspend fun clearIfCurrent(rejectedRefreshToken: String): Boolean
}

/**
 * Port để lấy access token mới, `core/auth` implement bằng token endpoint của Keycloak. Network layer
 * không biết IdP là ai — chỉ cần biết kết quả thuộc loại nào.
 */
fun interface TokenRefresher {
    suspend fun refresh(refreshToken: String): RefreshOutcome
}

sealed interface RefreshOutcome {
    data class Refreshed(val tokens: AuthTokens) : RefreshOutcome

    /** IdP reject refresh token (hết hạn, bị revoke, reuse) — phải clear session. */
    data object Rejected : RefreshOutcome

    /**
     * IdP tạm thời không reachable (mất mạng, 5xx). Giữ nguyên session vì app offline-first: mất mạng
     * không được đá user ra màn login.
     */
    data object Unavailable : RefreshOutcome
}

/**
 * Ktor cache bearer token đã load. Gọi [invalidate] sau login/logout để request kế tiếp đọc lại từ
 * [TokenProvider] thay vì gửi header cũ hoặc thiếu header.
 */
fun interface TokenCache {
    fun invalidate()
}
