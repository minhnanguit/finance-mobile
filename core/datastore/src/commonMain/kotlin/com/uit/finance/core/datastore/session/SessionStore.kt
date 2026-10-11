package com.uit.finance.core.datastore.session

import kotlinx.coroutines.flow.Flow

/** What we persist about the signed-in user. Tokens live in secure storage only. */
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochSeconds: Long,
    val userId: String? = null,
)

interface SessionStore {
    /** Emits the current session and every change; `null` means signed out. */
    val session: Flow<StoredSession?>
    suspend fun current(): StoredSession?

    /** Một lần đăng nhập mới. */
    suspend fun save(session: StoredSession)

    /**
     * Gắn [userId] (lấy từ `/me`) vào phiên **chỉ khi** phiên vẫn dùng refresh token [forRefreshToken], tức
     * là đúng phiên đã gọi `/me`. Phiên đổi giữa chừng (đăng xuất, người khác đăng nhập) thì không gắn: gắn
     * nhầm sẽ mở sổ của người này bằng token của người kia (ADR-006 B5).
     *
     * @return `false` nếu phiên đã đổi.
     */
    suspend fun attachUserId(userId: String, forRefreshToken: String): Boolean

    suspend fun clear()
}
