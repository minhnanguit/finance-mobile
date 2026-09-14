package com.mosaicglobal.finance.core.network.client

import com.mosaicglobal.finance.core.testing.FakeUuidGenerator
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdempotencyKeyPluginTest {

    @Test
    fun addsUuidHeaderToWritesOnly() = runTest {
        val uuid = FakeUuidGenerator()
        val seen = mutableListOf<Pair<String, String?>>()
        val client = testClient(uuidGenerator = uuid) { request ->
            seen += request.method.value to request.headers[IDEMPOTENCY_KEY_HEADER]
            jsonResponse("{}")
        }

        client.post("/api/v1/things") { setBody("{}") }
        client.put("/api/v1/things/1") { setBody("{}") }
        client.get("/api/v1/things")

        assertEquals("00000000-0000-0000-0000-000000000001", seen[0].second)
        assertEquals("00000000-0000-0000-0000-000000000002", seen[1].second)
        assertNull(seen[2].second, "GET must not carry an Idempotency-Key")
    }

    @Test
    fun keepsAnExplicitKey() = runTest {
        var received: String? = null
        val client = testClient { request ->
            received = request.headers[IDEMPOTENCY_KEY_HEADER]
            jsonResponse("{}")
        }

        client.post("/api/v1/things") {
            header(IDEMPOTENCY_KEY_HEADER, "caller-key")
            setBody("{}")
        }

        assertEquals("caller-key", received)
    }
}
