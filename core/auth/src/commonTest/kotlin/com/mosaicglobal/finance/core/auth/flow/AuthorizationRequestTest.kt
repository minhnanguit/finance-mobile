package com.mosaicglobal.finance.core.auth.flow

import com.mosaicglobal.finance.core.auth.AuthorizationPrompt
import com.mosaicglobal.finance.core.auth.testing.AUTH_ENDPOINT
import com.mosaicglobal.finance.core.auth.testing.REDIRECT_URI
import com.mosaicglobal.finance.core.auth.testing.testConfig
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthorizationRequestTest {

    @Test
    fun carriesEveryRequiredParameterForCodeFlowWithPkce() {
        val url = Url(authorizationUrl(AUTH_ENDPOINT, testConfig, "challenge-1", "state-1", AuthorizationPrompt.SignIn))

        assertEquals(AUTH_ENDPOINT, "${url.protocol.name}://${url.host}${url.encodedPath}")
        with(url.parameters) {
            assertEquals("code", get("response_type"))
            assertEquals("finance-mobile", get("client_id"))
            assertEquals(REDIRECT_URI, get("redirect_uri"))
            assertEquals("openid profile email", get("scope"))
            assertEquals("state-1", get("state"))
            assertEquals("challenge-1", get("code_challenge"))
            assertEquals("S256", get("code_challenge_method"))
            assertNull(get("prompt"))
        }
    }

    @Test
    fun signUpOpensTheRegistrationForm() {
        val url = Url(authorizationUrl(AUTH_ENDPOINT, testConfig, "c", "s", AuthorizationPrompt.SignUp))

        assertEquals("create", url.parameters["prompt"])
    }

    @Test
    fun parsesCodeStateAndIssuerFromTheRegisteredRedirect() {
        val callback = parseCallback("$REDIRECT_URI?code=abc&state=xyz&iss=http%3A%2F%2Fidp", REDIRECT_URI)

        assertEquals(AuthorizationCallback("abc", "xyz", null, null, "http://idp"), callback)
    }

    @Test
    fun rejectsACallbackOnAnyOtherUri() {
        assertNull(parseCallback("com.evil.app://oauth/callback?code=abc&state=xyz", REDIRECT_URI))
        assertNull(parseCallback("com.mosaicglobal.finance://oauth/other?code=abc", REDIRECT_URI))
        assertNull(parseCallback("com.mosaicglobal.finance://phish/callback?code=abc", REDIRECT_URI))
    }
}
