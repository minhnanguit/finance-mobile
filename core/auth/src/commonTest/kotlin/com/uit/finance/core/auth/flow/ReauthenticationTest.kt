package com.uit.finance.core.auth.flow

import com.uit.finance.core.auth.ReauthenticationOutcome
import com.uit.finance.core.auth.TokenClaims
import com.uit.finance.core.auth.launcher.LaunchResult
import com.uit.finance.core.auth.pkce.PkceGenerator
import com.uit.finance.core.auth.testing.FakeAuthorizationLauncher
import com.uit.finance.core.auth.testing.FakeKeycloak
import com.uit.finance.core.auth.testing.REDIRECT_URI
import com.uit.finance.core.auth.testing.json
import com.uit.finance.core.auth.testing.testConfig
import com.uit.finance.core.auth.testing.tokenJson
import com.uit.finance.core.common.result.AppResult
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Xác nhận lại trước khi xoá tài khoản (ADR-008 A2): bắt nhập lại, và chỉ nhận đúng tài khoản. */
class ReauthenticationTest {

    private val keycloak = FakeKeycloak()
    private val launcher = FakeAuthorizationLauncher()
    private val logouts = mutableListOf<String?>()

    private fun authenticator() =
        DefaultOidcAuthenticator(keycloak.client(), launcher, PkceGenerator(), testConfig, co.touchlab.kermit.Logger.withTag("test"))

    private val redirect: (String) -> LaunchResult = { url ->
        LaunchResult.Redirected("$REDIRECT_URI?code=c1&state=${Url(url).parameters["state"]}")
    }

    @Test
    fun forcesAFreshLoginWithoutPersonalDataInTheUrl() = runTest {
        launcher.respond = { LaunchResult.Cancelled }

        authenticator().reauthenticate(jwt(sub = "user-a"))

        val params = Url(launcher.launched.single()).parameters
        assertEquals("login", params["prompt"])
        assertEquals("0", params["max_age"])
        // URL đi qua lịch sử trình duyệt, log intent, access log của proxy: không mang email (ASVS V8.3.1).
        assertNull(params["login_hint"])
    }

    @Test
    fun sameAccountIsConfirmedWithTheFreshTokens() = runTest {
        launcher.respond = redirect
        keycloak.onToken = { json(tokenJson(access = jwt(sub = "user-a"), refresh = "fresh-refresh")) }

        val outcome = authenticator().reauthenticate(jwt(sub = "user-a"))

        val confirmed = assertIs<ReauthenticationOutcome.Confirmed>(assertIs<AppResult.Success<ReauthenticationOutcome>>(outcome).value)
        assertEquals("fresh-refresh", confirmed.tokens.refreshToken)
    }

    @Test
    fun anotherAccountIsRejectedAndItsSessionEnded() = runTest {
        launcher.respond = redirect
        keycloak.onToken = { json(tokenJson(access = jwt(sub = "user-b"), refresh = "other-refresh")) }
        keycloak.onLogout = { form ->
            logouts += form["refresh_token"]
            respond("", HttpStatusCode.NoContent)
        }

        val outcome = authenticator().reauthenticate(jwt(sub = "user-a"))

        assertEquals(AppResult.Success(ReauthenticationOutcome.DifferentAccount), outcome)
        assertEquals(listOf<String?>("other-refresh"), logouts, "phiên của tài khoản kia phải bị huỷ ngay")
    }

    @Test
    fun aTokenWithoutSubjectIsNeverTrusted() = runTest {
        launcher.respond = redirect
        keycloak.onToken = { json(tokenJson(access = "opaque-token")) }

        val outcome = authenticator().reauthenticate("opaque-current")

        assertEquals(AppResult.Success(ReauthenticationOutcome.DifferentAccount), outcome)
    }

    @Test
    fun userClosingTheBrowserIsACancellation() = runTest {
        launcher.respond = { LaunchResult.Cancelled }

        val outcome = authenticator().reauthenticate(jwt(sub = "user-a"))

        assertEquals(AppResult.Success(ReauthenticationOutcome.Cancelled), outcome)
        assertTrue(keycloak.tokenForms.isEmpty())
    }

    @Test
    fun claimsAreReadForDisplayButMalformedTokensGiveNothing() {
        val profile = TokenClaims.profile(jwt(sub = "user-a", extra = ""","email":"ann@example.com","name":"Ann Example""""))

        assertEquals("user-a", profile?.subject)
        assertEquals("ann@example.com", profile?.email)
        assertEquals("Ann Example", profile?.displayName)
        assertNull(TokenClaims.profile("not.a-jwt"))
        assertNull(TokenClaims.profile("only-one-part"))
    }

    private fun jwt(sub: String, extra: String = ""): String {
        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = base64.encode("""{"alg":"RS256","typ":"JWT"}""".encodeToByteArray())
        val payload = base64.encode("""{"sub":"$sub"$extra}""".encodeToByteArray())
        return "$header.$payload.signature"
    }
}
