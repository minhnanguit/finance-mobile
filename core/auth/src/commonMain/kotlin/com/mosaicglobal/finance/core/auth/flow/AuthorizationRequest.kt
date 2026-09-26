package com.mosaicglobal.finance.core.auth.flow

import com.mosaicglobal.finance.core.auth.AuthorizationPrompt
import com.mosaicglobal.finance.core.auth.OidcConfig
import io.ktor.http.URLBuilder
import io.ktor.http.Url

internal fun authorizationUrl(
    authorizationEndpoint: String,
    config: OidcConfig,
    codeChallenge: String,
    state: String,
    prompt: AuthorizationPrompt,
): String = URLBuilder(authorizationEndpoint).apply {
    parameters.append("response_type", "code")
    parameters.append("client_id", config.clientId)
    parameters.append("redirect_uri", config.redirectUri)
    parameters.append("scope", config.scopes.joinToString(" "))
    parameters.append("state", state)
    parameters.append("code_challenge", codeChallenge)
    parameters.append("code_challenge_method", "S256")
    if (prompt == AuthorizationPrompt.SignUp) parameters.append("prompt", "create")
}.buildString()

internal data class AuthorizationCallback(
    val code: String?,
    val state: String?,
    val error: String?,
    val errorDescription: String?,
    /** RFC 9207: authorization server issuer identification — chống mix-up attack. */
    val issuer: String?,
)

/** Trả `null` nếu URI không phải redirect URI đã đăng ký — không bao giờ tin một callback lạ. */
internal fun parseCallback(callbackUri: String, expectedRedirectUri: String): AuthorizationCallback? {
    val callback = runCatching { Url(callbackUri) }.getOrNull() ?: return null
    val expected = Url(expectedRedirectUri)
    val sameTarget = callback.protocol.name == expected.protocol.name &&
        callback.host == expected.host &&
        callback.encodedPath == expected.encodedPath
    if (!sameTarget) return null
    val params = callback.parameters
    return AuthorizationCallback(
        code = params["code"],
        state = params["state"],
        error = params["error"],
        errorDescription = params["error_description"],
        issuer = params["iss"],
    )
}
