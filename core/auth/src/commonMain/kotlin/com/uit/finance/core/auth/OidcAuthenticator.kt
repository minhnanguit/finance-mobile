package com.uit.finance.core.auth

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.network.auth.AuthTokens

/** API mà data layer của feature dùng để login / logout với Keycloak. */
interface OidcAuthenticator {

    /**
     * Mở system browser tới trang của Keycloak (Authorization Code + PKCE), rồi đổi code lấy token.
     * User tự đóng browser thì trả [AuthorizationOutcome.Cancelled], không phải lỗi.
     */
    suspend fun authorize(prompt: AuthorizationPrompt = AuthorizationPrompt.SignIn): AppResult<AuthorizationOutcome>

    /** Huỷ session trên Keycloak bằng refresh token — không mở browser. */
    suspend fun endSession(refreshToken: String): AppResult<Unit>
}

enum class AuthorizationPrompt {
    SignIn,

    /** Mở thẳng form đăng ký (`prompt=create`). */
    SignUp,
}

sealed interface AuthorizationOutcome {
    data class Authorized(val tokens: AuthTokens) : AuthorizationOutcome
    data object Cancelled : AuthorizationOutcome
}
