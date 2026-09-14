package com.mosaicglobal.finance.core.network.client

import com.mosaicglobal.finance.core.common.id.UuidGenerator
import com.mosaicglobal.finance.core.network.auth.AuthTokens
import com.mosaicglobal.finance.core.network.auth.DeviceIdProvider
import com.mosaicglobal.finance.core.network.auth.TokenCache
import com.mosaicglobal.finance.core.network.auth.TokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger as KermitLogger

internal const val AUTH_PATH_PREFIX = "/api/v1/auth/"
internal const val REFRESH_PATH = "/api/v1/auth/refresh"

/**
 * One configured [HttpClient] for the whole app:
 * ContentNegotiation(kotlinx json) · Kermit logging · timeouts · base URL ·
 * bearer auth with refresh-token rotation · automatic `Idempotency-Key` · RFC 7807 -> [com.mosaicglobal.finance.core.network.api.ApiException].
 */
internal fun createHttpClient(
    engine: HttpClientEngine,
    config: NetworkConfig,
    json: Json,
    tokenProvider: TokenProvider,
    deviceIdProvider: DeviceIdProvider,
    uuidGenerator: UuidGenerator,
    logger: KermitLogger,
): HttpClient {
    // Kotlin resolves a bare `logger` / `uuidGenerator` inside the config lambdas below to THESE parameters
    // (locals win over implicit receivers), so the config properties are assigned with an explicit `this.`.
    val kermit = logger
    val ids = uuidGenerator
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

        // Non-2xx -> ApiException. Installed by the client before user plugins, so its HttpSend interceptor
        // wraps the Auth plugin's: a 401 is first offered to Auth for a refresh + retry, and only the final
        // response is validated here.
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
                    if (refreshToken == null) {
                        null
                    } else {
                        rotate(refreshToken, deviceIdProvider, tokenProvider, kermit)
                    }
                }
                // Send the token proactively on every non-auth endpoint instead of waiting for a 401 challenge.
                sendWithoutRequest { request ->
                    !request.url.encodedPath.startsWith(AUTH_PATH_PREFIX)
                }
            }
        }
    }
}

/**
 * Calls `POST /api/v1/auth/refresh`. The presented refresh token is revoked server-side and replaced
 * (rotation); the new pair is persisted through [TokenProvider]. Any failure ends the session.
 */
private suspend fun io.ktor.client.plugins.auth.providers.RefreshTokensParams.rotate(
    refreshToken: String,
    deviceIdProvider: DeviceIdProvider,
    tokenProvider: TokenProvider,
    logger: KermitLogger,
): BearerTokens? = try {
    val response = client.post(REFRESH_PATH) {
        markAsRefreshTokenRequest()
        contentType(ContentType.Application.Json)
        setBody(RefreshRequestBody(refreshToken = refreshToken, deviceId = deviceIdProvider.deviceId()))
    }
    val pair = response.body<TokenPairBody>()
    tokenProvider.update(AuthTokens(pair.accessToken, pair.refreshToken, pair.expiresIn))
    BearerTokens(pair.accessToken, pair.refreshToken)
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    logger.w(e) { "Refresh token rotation failed; clearing session" }
    tokenProvider.clear()
    null
}

/** Wire shapes of `RefreshRequest` / `TokenPair` used by the refresh interceptor only. */
@Serializable
internal data class RefreshRequestBody(
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("deviceId") val deviceId: String,
)

@Serializable
internal data class TokenPairBody(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("tokenType") val tokenType: String = "Bearer",
    @SerialName("expiresIn") val expiresIn: Int,
)

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
