package com.uit.finance.core.network.client

import co.touchlab.kermit.Logger
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.core.network.auth.RefreshOutcome
import com.uit.finance.core.network.auth.TokenProvider
import com.uit.finance.core.network.auth.TokenRefresher
import com.uit.finance.core.testing.FakeUuidGenerator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal class FakeTokenProvider(initial: AuthTokens? = null) : TokenProvider {
    var stored: AuthTokens? = initial
    var clearCalls = 0
    override suspend fun tokens(): AuthTokens? = stored
    override suspend fun update(tokens: AuthTokens) { stored = tokens }
    override suspend fun clear() { stored = null; clearCalls += 1 }
}

/** Trả [outcome] cố định và ghi lại refresh token được đưa vào. */
internal class FakeTokenRefresher(var outcome: RefreshOutcome = RefreshOutcome.Rejected) : TokenRefresher {
    val presented = mutableListOf<String>()
    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        presented += refreshToken
        return outcome
    }
}

internal const val TEST_BASE_URL = "http://test.local"

internal fun testClient(
    tokenProvider: TokenProvider = FakeTokenProvider(),
    tokenRefresher: TokenRefresher = FakeTokenRefresher(),
    uuidGenerator: FakeUuidGenerator = FakeUuidGenerator(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = createHttpClient(
    engine = MockEngine(handler),
    config = NetworkConfig(baseUrl = TEST_BASE_URL),
    json = defaultJson(),
    tokenProvider = tokenProvider,
    tokenRefresher = tokenRefresher,
    uuidGenerator = uuidGenerator,
    logger = Logger.withTag("test"),
)

internal fun MockRequestHandleScope.jsonResponse(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

internal fun MockRequestHandleScope.problemResponse(status: HttpStatusCode, body: String): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/problem+json"))

internal const val UNAUTHORIZED_PROBLEM =
    """{"type":"about:blank","title":"Unauthorized","status":401,"code":"auth.unauthenticated"}"""
