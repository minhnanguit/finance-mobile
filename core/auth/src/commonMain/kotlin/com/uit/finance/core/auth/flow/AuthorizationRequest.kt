package com.uit.finance.core.auth.flow

import com.uit.finance.core.auth.AuthorizationPrompt
import com.uit.finance.core.auth.OidcConfig
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
    if (config.uiLocales.isNotEmpty()) parameters.append("ui_locales", config.uiLocales.joinToString(" "))
    when (prompt) {
        AuthorizationPrompt.SignIn -> Unit
        AuthorizationPrompt.SignUp -> parameters.append("prompt", "create")
        AuthorizationPrompt.Reauthenticate -> {
            // OIDC Core §3.1.2.1: prompt=login bắt nhập lại dù còn phiên SSO; max_age=0 để Keycloak ghi auth_time
            // mới vào token, backend kiểm mốc này (ADR-008 A2). Cố ý không gửi `login_hint`: email trong query string
            // lọt vào lịch sử trình duyệt, log intent của hệ thống, access log của proxy (ASVS V8.3.1). Còn phiên SSO
            // thì Keycloak tự hiện đúng tài khoản; hết phiên thì user gõ lại email — app vẫn kiểm `sub` sau đó.
            parameters.append("prompt", "login")
            parameters.append("max_age", "0")
        }
        AuthorizationPrompt.ChangePassword -> parameters.append("kc_action", KC_ACTION_UPDATE_PASSWORD)
    }
}.buildString()

internal data class AuthorizationCallback(
    val code: String?,
    val state: String?,
    val error: String?,
    val errorDescription: String?,
    /** RFC 9207: authorization server issuer identification — chống mix-up attack. */
    val issuer: String?,
    /** Kết quả của Application Initiated Action (`kc_action`): `success`, `cancelled` hoặc `error`. */
    val actionStatus: String? = null,
)

internal const val KC_ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD"

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
        actionStatus = params["kc_action_status"],
    )
}
