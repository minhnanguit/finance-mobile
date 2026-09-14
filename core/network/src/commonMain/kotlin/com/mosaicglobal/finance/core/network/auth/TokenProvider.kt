package com.mosaicglobal.finance.core.network.auth

/** Token pair as returned by the `/api/v1/auth/...` endpoints. */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    /** Access token lifetime in seconds as announced by the server. */
    val expiresInSeconds: Int,
)

/**
 * Port owned by the network layer, implemented by core/datastore (`SessionStore`).
 * The Ktor bearer plugin reads tokens here and writes rotated ones back.
 */
interface TokenProvider {
    suspend fun tokens(): AuthTokens?
    suspend fun update(tokens: AuthTokens)
    /** Called when the refresh token was rejected: the session is over. */
    suspend fun clear()
}

/** Stable installation identifier, required by `/auth/refresh` to bind the token to a device. */
fun interface DeviceIdProvider {
    suspend fun deviceId(): String
}

/**
 * Ktor caches the bearer tokens it loaded. Call [invalidate] after login/logout so the next
 * request re-reads them from [TokenProvider] instead of sending a stale/absent header.
 */
fun interface TokenCache {
    fun invalidate()
}
