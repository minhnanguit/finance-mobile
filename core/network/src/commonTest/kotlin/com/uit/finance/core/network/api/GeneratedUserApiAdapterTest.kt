package com.uit.finance.core.network.api

import com.uit.finance.core.network.api.internal.GeneratedUserApiAdapter
import com.uit.finance.core.network.client.TEST_BASE_URL
import com.uit.finance.core.network.client.jsonResponse
import com.uit.finance.core.network.client.testClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeneratedUserApiAdapterTest {

    @Test
    fun `xoá tài khoản gửi DELETE me kèm Accept json và nhận 202 có body`() = runTest {
        var seen: HttpRequestData? = null
        val client = testClient { request ->
            seen = request
            jsonResponse("""{"status":"DELETING","requestedAt":"2026-11-07T01:00:00Z"}""", HttpStatusCode.Accepted)
        }

        GeneratedUserApiAdapter(TEST_BASE_URL, client).deleteCurrentUser()

        val request = assertNotNull(seen)
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/me", request.url.encodedPath)
        // Server chọn handler theo Accept: contract phải `produces` application/json cho endpoint này (2.3.0),
        // nếu không Spring trả 406. Test giữ giả định đó hiển nhiên ở phía app.
        assertTrue(request.headers.getAll(HttpHeaders.Accept).orEmpty().any { "application/json" in it })
    }
}
