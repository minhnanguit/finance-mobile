package com.uit.finance.core.auth.testing

import com.uit.finance.core.auth.OidcConfig
import com.uit.finance.core.auth.client.OidcClient
import com.uit.finance.core.auth.client.createOidcHttpClient
import com.uit.finance.core.auth.launcher.AuthorizationLauncher
import com.uit.finance.core.auth.launcher.LaunchResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString

internal const val ISSUER = "http://idp.test/realms/finance"
internal const val REDIRECT_URI = "com.uit.finance://oauth/callback"
internal const val AUTH_ENDPOINT = "$ISSUER/protocol/openid-connect/auth"
internal const val TOKEN_ENDPOINT = "$ISSUER/protocol/openid-connect/token"
internal const val LOGOUT_ENDPOINT = "$ISSUER/protocol/openid-connect/logout"

internal val testConfig = OidcConfig(issuer = ISSUER, clientId = "finance-mobile", redirectUri = REDIRECT_URI)

internal fun discoveryJson(issuer: String = ISSUER): String = """
    {"issuer":"$issuer","authorization_endpoint":"$AUTH_ENDPOINT","token_endpoint":"$TOKEN_ENDPOINT",
     "end_session_endpoint":"$LOGOUT_ENDPOINT","jwks_uri":"$ISSUER/protocol/openid-connect/certs"}
""".trimIndent()

internal fun tokenJson(access: String = "access-1", refresh: String? = "refresh-1", expiresIn: Int = 300): String =
    buildString {
        append("""{"access_token":"$access","token_type":"Bearer","expires_in":$expiresIn""")
        refresh?.let { append(""","refresh_token":"$it"""") }
        append("}")
    }

internal fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

internal fun MockRequestHandleScope.oauthError(status: HttpStatusCode, error: String): HttpResponseData =
    json("""{"error":"$error","error_description":"described"}""", status)

internal suspend fun HttpRequestData.form(): Parameters = parseQueryString(body.toByteArray().decodeToString())

/** Keycloak giả: trả discovery, rồi giao request token/logout cho [onToken]. */
internal class FakeKeycloak(
    var discovery: String = discoveryJson(),
    var onToken: suspend MockRequestHandleScope.(Parameters) -> HttpResponseData = { json(tokenJson()) },
    var onLogout: suspend MockRequestHandleScope.(Parameters) -> HttpResponseData = { respond("", HttpStatusCode.NoContent) },
) {
    val discoveryCalls = mutableListOf<String>()
    val tokenForms = mutableListOf<Parameters>()

    val engine = MockEngine { request ->
        when (request.url.toString()) {
            "$ISSUER/.well-known/openid-configuration" -> {
                discoveryCalls += request.url.toString()
                json(discovery)
            }
            TOKEN_ENDPOINT -> request.form().also { tokenForms += it }.let { onToken(it) }
            LOGOUT_ENDPOINT -> onLogout(request.form())
            else -> respond("not found", HttpStatusCode.NotFound)
        }
    }

    fun client(): OidcClient = OidcClient(http = createOidcHttpClient(engine), config = testConfig)
}

/** Launcher giả: ghi lại URL được mở và trả kết quả do test quyết định từ chính URL đó. */
internal class FakeAuthorizationLauncher(
    var respond: (authorizationUrl: String) -> LaunchResult = { LaunchResult.Cancelled },
) : AuthorizationLauncher {
    val launched = mutableListOf<String>()
    override suspend fun launch(authorizationUrl: String, redirectUri: String): LaunchResult {
        launched += authorizationUrl
        return respond(authorizationUrl)
    }
}
