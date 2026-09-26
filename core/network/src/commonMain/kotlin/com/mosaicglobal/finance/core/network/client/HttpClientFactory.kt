package com.mosaicglobal.finance.core.network.client

import com.mosaicglobal.finance.core.common.id.UuidGenerator
import com.mosaicglobal.finance.core.network.auth.RefreshOutcome
import com.mosaicglobal.finance.core.network.auth.TokenCache
import com.mosaicglobal.finance.core.network.auth.TokenProvider
import com.mosaicglobal.finance.core.network.auth.TokenRefresher
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger as KermitLogger

/**
 * Một [HttpClient] cấu hình sẵn cho toàn app, chỉ để gọi backend: ContentNegotiation · logging đã
 * sanitize · timeout · base URL · bearer token có refresh · `Idempotency-Key` tự động · RFC 7807 →
 * [com.mosaicglobal.finance.core.network.api.ApiException].
 *
 * Refresh token KHÔNG đi qua client này: [TokenRefresher] gọi thẳng Keycloak bằng client riêng, nên
 * không có vòng lặp 401 → refresh → 401 và access token không bao giờ bị gửi tới Keycloak.
 */
internal fun createHttpClient(
    engine: HttpClientEngine,
    config: NetworkConfig,
    json: Json,
    tokenProvider: TokenProvider,
    tokenRefresher: TokenRefresher,
    uuidGenerator: UuidGenerator,
    logger: KermitLogger,
): HttpClient {
    // Trong các config lambda bên dưới, `logger` / `uuidGenerator` trơn sẽ resolve về CHÍNH các parameter
    // này (local thắng implicit receiver), nên property của config phải gán bằng `this.` tường minh.
    val kermit = logger
    val ids = uuidGenerator
    val apiHost = Url(config.baseUrl).host
    return HttpClient(engine) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMillis
            requestTimeoutMillis = config.requestTimeoutMillis
            socketTimeoutMillis = config.socketTimeoutMillis
        }

        defaultRequest {
            url(config.baseUrl)
        }

        install(Logging) {
            level = if (config.logHttp) LogLevel.HEADERS else LogLevel.NONE
            this.logger = object : KtorLogger {
                override fun log(message: String) {
                    kermit.d { message }
                }
            }
            sanitizeHeader { header -> header == HttpHeaders.Authorization || header == HttpHeaders.Cookie }
        }

        install(IdempotencyKeyPlugin) {
            this.uuidGenerator = ids
        }

        // Non-2xx → ApiException. Client cài validator này trước user plugin, nên interceptor HttpSend
        // của nó bọc ngoài Auth plugin: 401 được đưa cho Auth refresh + retry trước, chỉ response cuối
        // cùng mới bị validate ở đây.
        HttpResponseValidator {
            validateResponse { response ->
                if (!response.status.isSuccess()) {
                    throw response.toApiException(json)
                }
            }
        }

        install(Auth) {
            bearer {
                loadTokens {
                    tokenProvider.tokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                }
                refreshTokens {
                    val refreshToken = oldTokens?.refreshToken ?: tokenProvider.tokens()?.refreshToken
                    refreshToken?.let { refresh(it, tokenRefresher, tokenProvider, kermit) }
                }
                // Gửi token chủ động, nhưng CHỈ tới host của backend — token không được lọt sang host khác.
                sendWithoutRequest { request -> request.url.host == apiHost }
            }
        }
    }
}

/**
 * Ktor bearer plugin đã serialize các lần refresh đồng thời, nên hàm này chỉ chạy một lần cho mỗi đợt
 * 401. Trả `null` nghĩa là không có token mới: request gốc giữ nguyên 401.
 */
private suspend fun refresh(
    refreshToken: String,
    tokenRefresher: TokenRefresher,
    tokenProvider: TokenProvider,
    logger: KermitLogger,
): BearerTokens? = try {
    when (val outcome = tokenRefresher.refresh(refreshToken)) {
        is RefreshOutcome.Refreshed -> {
            tokenProvider.update(outcome.tokens)
            BearerTokens(outcome.tokens.accessToken, outcome.tokens.refreshToken)
        }
        RefreshOutcome.Rejected -> {
            logger.i { "Refresh token bị IdP reject; clear session" }
            tokenProvider.clear()
            null
        }
        RefreshOutcome.Unavailable -> {
            logger.w { "IdP tạm không reachable; giữ session, request này fail" }
            null
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Lỗi không lường trước: coi như Unavailable. Không clear session vì chưa chắc refresh token đã chết.
    logger.w(e) { "Refresh lỗi bất thường; giữ session" }
    null
}

internal class KtorTokenCache(private val client: HttpClient) : TokenCache {
    override fun invalidate() {
        client.authProviders.filterIsInstance<BearerAuthProvider>().forEach { it.clearToken() }
    }
}

internal fun defaultJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}
