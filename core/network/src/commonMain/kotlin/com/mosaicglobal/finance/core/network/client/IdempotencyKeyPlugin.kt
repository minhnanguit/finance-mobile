package com.mosaicglobal.finance.core.network.client

import com.mosaicglobal.finance.core.common.id.UuidGenerator
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpMethod

const val IDEMPOTENCY_KEY_HEADER: String = "Idempotency-Key"

class IdempotencyKeyConfig {
    lateinit var uuidGenerator: UuidGenerator
}

/**
 * Adds a fresh `Idempotency-Key` UUID to every POST / PUT / PATCH that does not already carry one.
 * Because the header is set on the request builder, Ktor's retry plugin re-sends the *same* key,
 * which is exactly what the contract asks for ("reused verbatim on retries").
 */
val IdempotencyKeyPlugin = createClientPlugin("IdempotencyKey", ::IdempotencyKeyConfig) {
    val uuidGenerator = pluginConfig.uuidGenerator
    onRequest { request, _ ->
        val isWrite = request.method == HttpMethod.Post ||
            request.method == HttpMethod.Put ||
            request.method == HttpMethod.Patch
        if (isWrite && !request.headers.contains(IDEMPOTENCY_KEY_HEADER)) {
            request.headers.append(IDEMPOTENCY_KEY_HEADER, uuidGenerator.generate())
        }
    }
}
