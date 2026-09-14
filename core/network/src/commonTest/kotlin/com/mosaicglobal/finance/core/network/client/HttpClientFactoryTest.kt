package com.mosaicglobal.finance.core.network.client

import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.common.result.FieldError
import com.mosaicglobal.finance.core.network.api.ApiException
import com.mosaicglobal.finance.core.network.api.apiCall
import com.mosaicglobal.finance.core.network.auth.AuthTokens
import io.ktor.client.call.body
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpClientFactoryTest {

    @Test
    fun sendsBearerTokenProactivelyOnProtectedEndpoints() = runTest {
        val tokens = FakeTokenProvider(AuthTokens("access-1", "refresh-1", 900))
        val authHeaders = mutableListOf<String?>()
        val client = testClient(tokenProvider = tokens) { request ->
            authHeaders += request.headers[HttpHeaders.Authorization]
            jsonResponse("""{"ok":true}""")
        }

        client.get("/api/v1/me")
        client.post("/api/v1/auth/login")

        assertEquals("Bearer access-1", authHeaders[0])
        assertNull(authHeaders[1], "auth endpoints must not receive a stale bearer token")
    }

    @Test
    fun rotatesRefreshTokenOn401AndRetriesOriginalRequest() = runTest {
        val tokens = FakeTokenProvider(AuthTokens("expired", "refresh-1", 900))
        val calls = mutableListOf<String>()
        var refreshBody: String? = null
        val client = testClient(tokenProvider = tokens) { request ->
            calls += "${request.method.value} ${request.url.encodedPath} ${request.headers[HttpHeaders.Authorization]}"
            when {
                request.url.encodedPath == REFRESH_PATH -> {
                    refreshBody = request.body.toByteArray().decodeToString()
                    jsonResponse(tokenPairJson("access-2", "refresh-2"))
                }
                request.headers[HttpHeaders.Authorization] == "Bearer access-2" -> jsonResponse("""{"id":"u1"}""")
                else -> problemResponse(
                    HttpStatusCode.Unauthorized,
                    """{"type":"about:blank","title":"Unauthorized","status":401,"code":"identity.token_expired"}""",
                )
            }
        }

        val body = client.get("/api/v1/me").bodyAsText()

        assertEquals("""{"id":"u1"}""", body)
        assertEquals(
            listOf(
                "GET /api/v1/me Bearer expired",
                "POST $REFRESH_PATH null",
                "GET /api/v1/me Bearer access-2",
            ),
            calls,
        )
        assertTrue(refreshBody?.contains("\"refreshToken\":\"refresh-1\"") == true)
        assertTrue(refreshBody?.contains("\"deviceId\":\"device-1234\"") == true)
        assertEquals(AuthTokens("access-2", "refresh-2", 900), tokens.stored, "rotated pair must be persisted")
    }

    @Test
    fun clearsSessionWhenRefreshIsRejected() = runTest {
        val tokens = FakeTokenProvider(AuthTokens("expired", "reused-refresh", 900))
        val client = testClient(tokenProvider = tokens) { request ->
            problemResponse(
                HttpStatusCode.Unauthorized,
                if (request.url.encodedPath == REFRESH_PATH) {
                    """{"type":"about:blank","title":"Unauthorized","status":401,"code":"identity.refresh_reused"}"""
                } else {
                    """{"type":"about:blank","title":"Unauthorized","status":401,"code":"identity.token_expired"}"""
                },
            )
        }

        val result = apiCall { client.get("/api/v1/me").body<String>() }

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertNull(tokens.stored)
        assertEquals(1, tokens.clearCalls)
    }

    @Test
    fun mapsProblemDetailsToApiException() = runTest {
        val client = testClient {
            problemResponse(
                HttpStatusCode.Conflict,
                """
                {"type":"https://finance.example/problems/email-taken","title":"Conflict","status":409,
                 "detail":"Email already registered","code":"identity.email_taken","traceId":"abc123",
                 "errors":[{"field":"email","message":"already in use"}]}
                """.trimIndent(),
            )
        }

        val exception = assertFailsWith<ApiException> { client.post("/api/v1/auth/register") }

        assertEquals(409, exception.status)
        assertEquals("identity.email_taken", exception.code)
        assertEquals("Conflict", exception.title)
        assertEquals("Email already registered", exception.detail)
        assertEquals("abc123", exception.traceId)
        assertEquals(listOf(FieldError("email", "already in use")), exception.fieldErrors)
    }

    @Test
    fun apiCallTranslatesProblemsToAppError() = runTest {
        val client = testClient {
            problemResponse(
                HttpStatusCode.UnprocessableEntity,
                """{"type":"about:blank","title":"Unprocessable Entity","status":422,"code":"idempotency.payload_mismatch"}""",
            )
        }

        val result = apiCall { client.post("/api/v1/auth/login").body<String>() }

        val error = assertIs<AppResult.Failure>(result).error
        assertEquals(AppError.Api(422, "idempotency.payload_mismatch", "Unprocessable Entity", null), error)
    }

    @Test
    fun nonProblemErrorBodiesStillBecomeApiException() = runTest {
        val client = testClient { respondError(HttpStatusCode.BadGateway, "<html>upstream down</html>") }

        val exception = assertFailsWith<ApiException> { client.get("/api/v1/me") }

        assertEquals(502, exception.status)
        assertNull(exception.code)
        assertEquals("Bad Gateway", exception.title)
    }
}
