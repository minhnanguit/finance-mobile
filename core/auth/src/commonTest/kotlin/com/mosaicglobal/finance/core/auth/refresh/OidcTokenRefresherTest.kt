package com.mosaicglobal.finance.core.auth.refresh

import co.touchlab.kermit.Logger
import com.mosaicglobal.finance.core.auth.testing.FakeKeycloak
import com.mosaicglobal.finance.core.auth.testing.json
import com.mosaicglobal.finance.core.auth.testing.oauthError
import com.mosaicglobal.finance.core.auth.testing.tokenJson
import com.mosaicglobal.finance.core.network.auth.AuthTokens
import com.mosaicglobal.finance.core.network.auth.RefreshOutcome
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OidcTokenRefresherTest {

    private val keycloak = FakeKeycloak()

    private fun refresher() = OidcTokenRefresher(keycloak.client(), Logger.withTag("test"))

    @Test
    fun storesTheRotatedRefreshToken() = runTest {
        keycloak.onToken = { json(tokenJson(access = "access-2", refresh = "refresh-2")) }

        assertEquals(RefreshOutcome.Refreshed(AuthTokens("access-2", "refresh-2", 300)), refresher().refresh("refresh-1"))
        val form = keycloak.tokenForms.single()
        assertEquals("refresh_token", form["grant_type"])
        assertEquals("refresh-1", form["refresh_token"])
        assertEquals("finance-mobile", form["client_id"])
    }

    @Test
    fun keepsTheOldRefreshTokenWhenTheIdpDoesNotRotate() = runTest {
        keycloak.onToken = { json(tokenJson(access = "access-2", refresh = null)) }

        assertEquals(RefreshOutcome.Refreshed(AuthTokens("access-2", "refresh-1", 300)), refresher().refresh("refresh-1"))
    }

    @Test
    fun invalidGrantMeansTheSessionIsGone() = runTest {
        keycloak.onToken = { oauthError(HttpStatusCode.BadRequest, "invalid_grant") }

        assertEquals(RefreshOutcome.Rejected, refresher().refresh("revoked"))
    }

    @Test
    fun anyOther4xxIsAlsoFinal() = runTest {
        keycloak.onToken = { oauthError(HttpStatusCode.BadRequest, "unauthorized_client") }

        assertEquals(RefreshOutcome.Rejected, refresher().refresh("r"))
    }

    @Test
    fun aServerErrorIsTemporary() = runTest {
        keycloak.onToken = { oauthError(HttpStatusCode.ServiceUnavailable, "temporarily_unavailable") }

        assertEquals(RefreshOutcome.Unavailable, refresher().refresh("r"))
    }

    @Test
    fun aNetworkFailureIsTemporary() = runTest {
        keycloak.onToken = { throw kotlinx.io.IOException("network down") }

        assertEquals(RefreshOutcome.Unavailable, refresher().refresh("r"))
    }
}
