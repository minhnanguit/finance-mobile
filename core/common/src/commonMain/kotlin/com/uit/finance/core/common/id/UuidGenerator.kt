package com.uit.finance.core.common.id

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Client-side UUID source (entity ids, idempotency keys, installation id). Fake it in tests. */
fun interface UuidGenerator {
    fun generate(): String
}

@OptIn(ExperimentalUuidApi::class)
class RandomUuidGenerator : UuidGenerator {
    override fun generate(): String = Uuid.random().toString()
}
