package com.mosaicglobal.finance.core.auth.flow

import com.mosaicglobal.finance.core.auth.AuthorizationOutcome
import com.mosaicglobal.finance.core.auth.AuthorizationPrompt
import com.mosaicglobal.finance.core.auth.OidcAuthenticator
import com.mosaicglobal.finance.core.auth.OidcConfig
import com.mosaicglobal.finance.core.auth.client.OidcClient
import com.mosaicglobal.finance.core.auth.client.OidcDiscovery
import com.mosaicglobal.finance.core.auth.client.OidcException
import com.mosaicglobal.finance.core.auth.client.oidcCall
import com.mosaicglobal.finance.core.auth.client.toAuthTokens
import com.mosaicglobal.finance.core.auth.launcher.AuthorizationLauncher
import com.mosaicglobal.finance.core.auth.launcher.LaunchResult
import com.mosaicglobal.finance.core.auth.pkce.PkceGenerator
import com.mosaicglobal.finance.core.common.result.AppResult

internal class DefaultOidcAuthenticator(
    private val client: OidcClient,
    private val launcher: AuthorizationLauncher,
    private val pkceGenerator: PkceGenerator,
    private val config: OidcConfig,
) : OidcAuthenticator {

    override suspend fun authorize(prompt: AuthorizationPrompt): AppResult<AuthorizationOutcome> = oidcCall {
        val discovery = client.discovery()
        val pkce = pkceGenerator.pkce()
        val state = pkceGenerator.state()
        val url = authorizationUrl(discovery.authorizationEndpoint, config, pkce.challenge, state, prompt)

        when (val launched = launcher.launch(url, config.redirectUri)) {
            LaunchResult.Cancelled -> AuthorizationOutcome.Cancelled
            is LaunchResult.Failed -> throw OidcException.LaunchFailed(launched.reason)
            is LaunchResult.Redirected -> completeAuthorization(launched.callbackUri, state, pkce.verifier, discovery)
        }
    }

    override suspend fun endSession(refreshToken: String): AppResult<Unit> = oidcCall {
        client.endSession(refreshToken)
    }

    private suspend fun completeAuthorization(
        callbackUri: String,
        expectedState: String,
        codeVerifier: String,
        discovery: OidcDiscovery,
    ): AuthorizationOutcome {
        val callback = parseCallback(callbackUri, config.redirectUri)
            ?: throw OidcException.InvalidCallback("không phải redirect URI đã đăng ký")
        // Kiểm state TRƯỚC mọi thứ khác, kể cả khi callback báo lỗi.
        if (callback.state != expectedState) throw OidcException.StateMismatch()
        if (callback.issuer != null && callback.issuer != discovery.issuer) {
            throw OidcException.IssuerMismatch(discovery.issuer, callback.issuer)
        }
        callback.error?.let { error ->
            // access_denied = user từ chối trên trang Keycloak: coi như huỷ, không phải lỗi.
            if (error == "access_denied") return AuthorizationOutcome.Cancelled
            throw OidcException.Http(status = 400, error = error, description = callback.errorDescription)
        }
        val code = callback.code ?: throw OidcException.InvalidCallback("thiếu code")
        return AuthorizationOutcome.Authorized(client.exchangeCode(code, codeVerifier).toAuthTokens())
    }
}
