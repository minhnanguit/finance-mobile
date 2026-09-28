package com.uit.finance.core.auth.client

import com.uit.finance.core.network.auth.AuthTokens
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Phần của discovery document (OIDC Discovery 1.0) mà app cần. */
@Serializable
internal data class OidcDiscovery(
    @SerialName("issuer") val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("end_session_endpoint") val endSessionEndpoint: String? = null,
)

@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** Body lỗi chuẩn OAuth 2.0 (RFC 6749 §5.2) — Keycloak KHÔNG trả RFC 7807 ở các endpoint này. */
@Serializable
internal data class OAuthErrorBody(
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

/**
 * Keycloak rotate refresh token ở mỗi lần refresh. Nếu IdP không trả refresh token mới thì giữ cái cũ
 * ([fallbackRefreshToken]) thay vì làm mất session.
 */
internal fun TokenResponse.toAuthTokens(fallbackRefreshToken: String? = null): AuthTokens = AuthTokens(
    accessToken = accessToken,
    refreshToken = refreshToken ?: fallbackRefreshToken
        ?: throw OidcException.InvalidResponse("token endpoint không trả refresh_token"),
    expiresInSeconds = expiresIn,
)
