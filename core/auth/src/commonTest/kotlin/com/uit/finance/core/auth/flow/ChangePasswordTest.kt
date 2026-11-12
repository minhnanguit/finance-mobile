package com.uit.finance.core.auth.flow

import com.uit.finance.core.auth.PasswordChangeOutcome
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

/** Đổi mật khẩu qua Keycloak Application Initiated Action (`kc_action=UPDATE_PASSWORD`). */
class ChangePasswordTest {

    private val keycloak = FakeKeycloak()
    private val launcher = FakeAuthorizationLauncher()
    private val logouts = mutableListOf<String?>()

    private fun authenticator() =
        DefaultOidcAuthenticator(keycloak.client(), launcher, PkceGenerator(), testConfig, co.touchlab.kermit.Logger.withTag("test"))

    /** Redirect giống Keycloak 26: luôn kèm `code`, kết quả của action nằm ở `kc_action_status`. */
    private fun redirectWith(status: String?): (String) -> LaunchResult = { url ->
        val action = status?.let { "&kc_action=UPDATE_PASSWORD&kc_action_status=$it" }.orEmpty()
        LaunchResult.Redirected("$REDIRECT_URI?code=c1&state=${Url(url).parameters["state"]}$action")
    }

    @Test
    fun opensTheUpdatePasswordActionWithoutForcingAnotherLogin() = runTest {
        launcher.respond = { LaunchResult.Cancelled }

        authenticator().changePassword(jwt(sub = "user-a"))

        val params = Url(launcher.launched.single()).parameters
        assertEquals("UPDATE_PASSWORD", params["kc_action"])
        // Keycloak tự hỏi mật khẩu hiện tại khi lần đăng nhập đã cũ; không ép thêm một lần đăng nhập.
        assertNull(params["prompt"])
        assertNull(params["login_hint"])
    }

    @Test
    fun successForTheSameAccountReturnsTheTokensOfTheSurvivingSession() = runTest {
        launcher.respond = redirectWith("success")
        keycloak.onToken = { json(tokenJson(access = jwt(sub = "user-a"), refresh = "after-change")) }

        val outcome = authenticator().changePassword(jwt(sub = "user-a"))

        val changed = assertIs<PasswordChangeOutcome.Changed>(assertIs<AppResult.Success<PasswordChangeOutcome>>(outcome).value)
        assertEquals("after-change", changed.tokens.refreshToken)
    }

    @Test
    fun cancellingOnTheFormDoesNotExchangeTheCode() = runTest {
        launcher.respond = redirectWith("cancelled")

        val outcome = authenticator().changePassword(jwt(sub = "user-a"))

        assertEquals(AppResult.Success(PasswordChangeOutcome.Cancelled), outcome)
        assertTrue(keycloak.tokenForms.isEmpty())
    }

    @Test
    fun closingTheBrowserIsACancellation() = runTest {
        launcher.respond = { LaunchResult.Cancelled }

        assertEquals(AppResult.Success(PasswordChangeOutcome.Cancelled), authenticator().changePassword(jwt(sub = "user-a")))
    }

    @Test
    fun changingAnotherAccountsPasswordEndsThatSession() = runTest {
        launcher.respond = redirectWith("success")
        keycloak.onToken = { json(tokenJson(access = jwt(sub = "user-b"), refresh = "other-refresh")) }
        keycloak.onLogout = { form ->
            logouts += form["refresh_token"]
            respond("", HttpStatusCode.NoContent)
        }

        val outcome = authenticator().changePassword(jwt(sub = "user-a"))

        assertEquals(AppResult.Success(PasswordChangeOutcome.DifferentAccount), outcome)
        assertEquals(listOf<String?>("other-refresh"), logouts)
    }

    @Test
    fun anErrorOrMissingActionStatusIsAFailure() = runTest {
        for (status in listOf("error", null)) {
            launcher.respond = redirectWith(status)

            val outcome = authenticator().changePassword(jwt(sub = "user-a"))

            assertIs<AppResult.Failure>(outcome, "kc_action_status=$status")
        }
        assertTrue(keycloak.tokenForms.isEmpty())
    }

    private fun jwt(sub: String): String {
        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = base64.encode("""{"alg":"RS256","typ":"JWT"}""".encodeToByteArray())
        val payload = base64.encode("""{"sub":"$sub"}""".encodeToByteArray())
        return "$header.$payload.signature"
    }
}
