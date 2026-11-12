package com.uit.finance.core.auth.flow

import co.touchlab.kermit.Logger
import com.uit.finance.core.auth.AuthorizationOutcome
import com.uit.finance.core.auth.AuthorizationPrompt
import com.uit.finance.core.auth.OidcAuthenticator
import com.uit.finance.core.auth.OidcConfig
import com.uit.finance.core.auth.PasswordChangeOutcome
import com.uit.finance.core.auth.ReauthenticationOutcome
import com.uit.finance.core.auth.TokenClaims
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.core.auth.client.OidcClient
import com.uit.finance.core.auth.client.OidcDiscovery
import com.uit.finance.core.auth.client.OidcException
import com.uit.finance.core.auth.client.oidcCall
import com.uit.finance.core.auth.client.toAuthTokens
import com.uit.finance.core.auth.launcher.AuthorizationLauncher
import com.uit.finance.core.auth.launcher.LaunchResult
import com.uit.finance.core.auth.pkce.PkceGenerator
import com.uit.finance.core.common.result.AppResult
import kotlinx.coroutines.CancellationException

internal class DefaultOidcAuthenticator(
    private val client: OidcClient,
    private val launcher: AuthorizationLauncher,
    private val pkceGenerator: PkceGenerator,
    private val config: OidcConfig,
    private val logger: Logger,
) : OidcAuthenticator {

    override suspend fun authorize(prompt: AuthorizationPrompt): AppResult<AuthorizationOutcome> =
        oidcCall(logger) { runAuthorization(prompt) }

    override suspend fun reauthenticate(currentAccessToken: String): AppResult<ReauthenticationOutcome> = oidcCall(logger) {
        when (val outcome = runForAccount(AuthorizationPrompt.Reauthenticate, currentAccessToken)) {
            AccountOutcome.Cancelled -> ReauthenticationOutcome.Cancelled
            AccountOutcome.DifferentAccount -> ReauthenticationOutcome.DifferentAccount
            is AccountOutcome.SameAccount -> ReauthenticationOutcome.Confirmed(outcome.tokens)
        }
    }

    override suspend fun changePassword(currentAccessToken: String): AppResult<PasswordChangeOutcome> = oidcCall(logger) {
        when (val outcome = runForAccount(AuthorizationPrompt.ChangePassword, currentAccessToken)) {
            AccountOutcome.Cancelled -> PasswordChangeOutcome.Cancelled
            AccountOutcome.DifferentAccount -> PasswordChangeOutcome.DifferentAccount
            is AccountOutcome.SameAccount -> PasswordChangeOutcome.Changed(outcome.tokens)
        }
    }

    /**
     * Chạy [prompt] trong trình duyệt rồi chỉ nhận token khi thuộc đúng tài khoản của [currentAccessToken] (so `sub`).
     * Tài khoản khác (hoặc không đọc được `sub`) thì huỷ phiên vừa mở, không dùng token nào.
     */
    private suspend fun runForAccount(prompt: AuthorizationPrompt, currentAccessToken: String): AccountOutcome {
        val expectedSubject = TokenClaims.subject(currentAccessToken)
        return when (val outcome = runAuthorization(prompt)) {
            AuthorizationOutcome.Cancelled -> AccountOutcome.Cancelled
            is AuthorizationOutcome.Authorized -> {
                val subject = TokenClaims.subject(outcome.tokens.accessToken)
                if (expectedSubject != null && subject == expectedSubject) {
                    AccountOutcome.SameAccount(outcome.tokens)
                } else {
                    logger.w { "$prompt bằng tài khoản khác; huỷ phiên vừa mở" }
                    endQuietly(outcome.tokens.refreshToken)
                    AccountOutcome.DifferentAccount
                }
            }
        }
    }

    /** Best effort: huỷ không được thì phiên đó tự hết hạn; không chặn kết quả trả về cho user. */
    private suspend fun endQuietly(refreshToken: String) {
        try {
            client.endSession(refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w { "Không huỷ được phiên vừa mở: ${e::class.simpleName}" }
        }
    }

    private suspend fun runAuthorization(prompt: AuthorizationPrompt): AuthorizationOutcome {
        val discovery = client.discovery()
        val pkce = pkceGenerator.pkce()
        val state = pkceGenerator.state()
        val url = authorizationUrl(discovery.authorizationEndpoint, config, pkce.challenge, state, prompt)

        return when (val launched = launcher.launch(url, config.redirectUri)) {
            LaunchResult.Cancelled -> AuthorizationOutcome.Cancelled
            is LaunchResult.Failed -> throw OidcException.LaunchFailed(launched.reason)
            is LaunchResult.Redirected ->
                completeAuthorization(launched.callbackUri, state, pkce.verifier, discovery, prompt)
        }
    }

    override suspend fun endSession(refreshToken: String): AppResult<Unit> = oidcCall(logger) {
        client.endSession(refreshToken)
    }

    private suspend fun completeAuthorization(
        callbackUri: String,
        expectedState: String,
        codeVerifier: String,
        discovery: OidcDiscovery,
        prompt: AuthorizationPrompt,
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
        if (prompt == AuthorizationPrompt.ChangePassword) {
            when (callback.actionStatus) {
                "success" -> Unit
                // Huỷ trên form: Keycloak vẫn kèm code của phiên đăng nhập, nhưng không có gì cần đổi.
                "cancelled" -> return AuthorizationOutcome.Cancelled
                else -> throw OidcException.ActionFailed(KC_ACTION_UPDATE_PASSWORD, callback.actionStatus)
            }
        }
        val code = callback.code ?: throw OidcException.InvalidCallback("thiếu code")
        return AuthorizationOutcome.Authorized(client.exchangeCode(code, codeVerifier).toAuthTokens())
    }
}

/** Kết quả chung của các luồng phải đúng tài khoản đang đăng nhập (xác nhận lại, đổi mật khẩu). */
private sealed interface AccountOutcome {
    data class SameAccount(val tokens: AuthTokens) : AccountOutcome
    data object Cancelled : AccountOutcome
    data object DifferentAccount : AccountOutcome
}
