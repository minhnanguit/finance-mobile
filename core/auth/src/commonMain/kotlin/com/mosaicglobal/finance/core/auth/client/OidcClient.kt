package com.mosaicglobal.finance.core.auth.client

import com.mosaicglobal.finance.core.auth.OidcConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Nói chuyện trực tiếp với Keycloak. Dùng HttpClient RIÊNG, không phải client gọi backend: không có
 * bearer plugin (tránh vòng lặp 401 → refresh) và không có logging (body chứa token).
 */
internal class OidcClient(
    private val http: HttpClient,
    private val config: OidcConfig,
) {
    private val discoveryLock = Mutex()

    @Volatile
    private var cachedDiscovery: OidcDiscovery? = null

    /** Tải một lần rồi cache trong memory; lỗi thì không cache để lần sau thử lại. */
    suspend fun discovery(): OidcDiscovery = cachedDiscovery ?: discoveryLock.withLock {
        cachedDiscovery ?: fetchDiscovery().also { cachedDiscovery = it }
    }

    suspend fun exchangeCode(code: String, codeVerifier: String): TokenResponse = tokenRequest(
        parameters {
            append("grant_type", "authorization_code")
            append("code", code)
            append("redirect_uri", config.redirectUri)
            append("client_id", config.clientId)
            append("code_verifier", codeVerifier)
        },
    )

    suspend fun refresh(refreshToken: String): TokenResponse = tokenRequest(
        parameters {
            append("grant_type", "refresh_token")
            append("refresh_token", refreshToken)
            append("client_id", config.clientId)
        },
    )

    /**
     * Logout phía server bằng refresh token: Keycloak huỷ cả user session, nên SSO cookie còn trong
     * browser cũng vô hiệu. Không cần mở browser tới `end_session_endpoint`.
     */
    suspend fun endSession(refreshToken: String) {
        val endpoint = discovery().endSessionEndpoint
            ?: throw OidcException.InvalidResponse("discovery không có end_session_endpoint")
        http.submitForm(
            url = endpoint,
            formParameters = parameters {
                append("client_id", config.clientId)
                append("refresh_token", refreshToken)
            },
        ).ensureSuccess()
    }

    private suspend fun fetchDiscovery(): OidcDiscovery {
        val response = http.get("${config.issuer}/.well-known/openid-configuration")
        response.ensureSuccess()
        val document = response.body<OidcDiscovery>()
        // OIDC Discovery §4.3: issuer trong document PHẢI trùng issuer đã cấu hình, chống IdP giả mạo.
        if (document.issuer != config.issuer) throw OidcException.IssuerMismatch(config.issuer, document.issuer)
        return document
    }

    private suspend fun tokenRequest(form: Parameters): TokenResponse {
        val response = http.submitForm(url = discovery().tokenEndpoint, formParameters = form)
        response.ensureSuccess()
        val token = response.body<TokenResponse>()
        if (!token.tokenType.equals("Bearer", ignoreCase = true)) {
            throw OidcException.InvalidResponse("token_type không hỗ trợ: ${token.tokenType}")
        }
        return token
    }
}

private suspend fun HttpResponse.ensureSuccess() {
    if (status.isSuccess()) return
    val body = runCatching { body<OAuthErrorBody>() }.getOrNull()
    throw OidcException.Http(status = status.value, error = body?.error, description = body?.errorDescription)
}

internal fun createOidcHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
}
