package com.mosaicglobal.finance.core.auth.flow

import com.mosaicglobal.finance.core.auth.AuthorizationOutcome
import com.mosaicglobal.finance.core.auth.AuthorizationPrompt
import com.mosaicglobal.finance.core.auth.launcher.LaunchResult
import com.mosaicglobal.finance.core.auth.pkce.PkceGenerator
import com.mosaicglobal.finance.core.auth.testing.FakeAuthorizationLauncher
import com.mosaicglobal.finance.core.auth.testing.FakeKeycloak
import com.mosaicglobal.finance.core.auth.testing.REDIRECT_URI
import com.mosaicglobal.finance.core.auth.testing.discoveryJson
import com.mosaicglobal.finance.core.auth.testing.oauthError
import com.mosaicglobal.finance.core.auth.testing.testConfig
import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.network.auth.AuthTokens
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultOidcAuthenticatorTest {

    private val keycloak = FakeKeycloak()
    private val launcher = FakeAuthorizationLauncher()
    private val pkce = PkceGenerator()

    private fun authenticator() = DefaultOidcAuthenticator(keycloak.client(), launcher, pkce, testConfig)

    /** Giả lập Keycloak redirect về đúng `state` nó nhận được, kèm [extra]. */
    private fun redirectEchoingState(extra: String = "code=auth-code-1"): (String) -> LaunchResult = { url ->
        LaunchResult.Redirected("$REDIRECT_URI?$extra&state=${Url(url).parameters["state"]}")
    }

    @Test
    fun exchangesTheCodeWithTheVerifierThatMatchesTheChallenge() = runTest {
        launcher.respond = redirectEchoingState()

        val result = authenticator().authorize()

        assertEquals(
            AppResult.Success(AuthorizationOutcome.Authorized(AuthTokens("access-1", "refresh-1", 300))),
            result,
        )
        val challenge = Url(launcher.launched.single()).parameters["code_challenge"]
        val form = keycloak.tokenForms.single()
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("auth-code-1", form["code"])
        assertEquals(REDIRECT_URI, form["redirect_uri"])
        assertEquals(challenge, pkce.challengeFor(form["code_verifier"]!!), "verifier phải là nguồn của challenge")
    }

    @Test
    fun userClosingTheBrowserIsACancellationNotAnError() = runTest {
        launcher.respond = { LaunchResult.Cancelled }

        assertEquals(AppResult.Success(AuthorizationOutcome.Cancelled), authenticator().authorize())
        assertTrue(keycloak.tokenForms.isEmpty())
    }

    @Test
    fun userDenyingOnTheKeycloakPageIsACancellation() = runTest {
        launcher.respond = redirectEchoingState(extra = "error=access_denied")

        assertEquals(AppResult.Success(AuthorizationOutcome.Cancelled), authenticator().authorize())
    }

    @Test
    fun aForgedStateIsRejectedBeforeAnyTokenRequest() = runTest {
        launcher.respond = { LaunchResult.Redirected("$REDIRECT_URI?code=stolen&state=attacker-state") }

        val error = assertIs<AppResult.Failure>(authenticator().authorize()).error

        assertIs<AppError.Unknown>(error)
        assertTrue(keycloak.tokenForms.isEmpty(), "không được đổi code khi state sai")
    }

    @Test
    fun aCallbackFromAnotherIssuerIsRejected() = runTest {
        launcher.respond = redirectEchoingState(extra = "code=c&iss=http%3A%2F%2Fevil.test%2Frealms%2Ffinance")

        assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(authenticator().authorize()).error)
        assertTrue(keycloak.tokenForms.isEmpty())
    }

    @Test
    fun aDiscoveryDocumentForAnotherIssuerIsRejected() = runTest {
        keycloak.discovery = discoveryJson(issuer = "http://evil.test/realms/finance")

        assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(authenticator().authorize()).error)
        assertTrue(launcher.launched.isEmpty(), "không được mở browser tới IdP giả")
    }

    @Test
    fun anExpiredCodeBecomesUnauthorized() = runTest {
        launcher.respond = redirectEchoingState()
        keycloak.onToken = { oauthError(HttpStatusCode.BadRequest, "invalid_grant") }

        assertEquals(AppResult.Failure(AppError.Unauthorized), authenticator().authorize())
    }

    @Test
    fun noBrowserOnTheDeviceIsAFailure() = runTest {
        launcher.respond = { LaunchResult.Failed("no browser") }

        assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(authenticator().authorize()).error)
    }

    @Test
    fun signUpAsksKeycloakForTheRegistrationForm() = runTest {
        authenticator().authorize(AuthorizationPrompt.SignUp)

        assertEquals("create", Url(launcher.launched.single()).parameters["prompt"])
    }

    @Test
    fun endSessionSendsTheRefreshTokenToTheLogoutEndpoint() = runTest {
        var logoutForm: Parameters? = null
        keycloak.onLogout = { form ->
            logoutForm = form
            respond("", HttpStatusCode.NoContent)
        }

        assertEquals(AppResult.Success(Unit), authenticator().endSession("refresh-1"))
        assertEquals("refresh-1", logoutForm?.get("refresh_token"))
        assertEquals("finance-mobile", logoutForm?.get("client_id"))
    }

    @Test
    fun discoveryIsFetchedOnceAndReused() = runTest {
        val auth = authenticator()
        auth.authorize()
        auth.authorize()

        assertEquals(1, keycloak.discoveryCalls.size)
    }
}
