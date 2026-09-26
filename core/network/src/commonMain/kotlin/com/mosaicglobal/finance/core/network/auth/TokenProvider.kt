package com.mosaicglobal.finance.core.network.auth

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
 */
interface TokenProvider {
    suspend fun tokens(): AuthTokens?
    suspend fun update(tokens: AuthTokens)
    /** Gọi khi refresh token bị IdP reject: session đã hết. */
    suspend fun clear()
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
