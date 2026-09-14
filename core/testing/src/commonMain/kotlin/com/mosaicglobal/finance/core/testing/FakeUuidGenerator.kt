package com.mosaicglobal.finance.core.testing

import com.mosaicglobal.finance.core.common.id.UuidGenerator

/** Emits predictable ids: `00000000-0000-0000-0000-000000000001`, `...002`, ... */
class FakeUuidGenerator : UuidGenerator {
    private var counter = 0L
    val generated = mutableListOf<String>()

    override fun generate(): String {
        counter += 1
        val id = "00000000-0000-0000-0000-" + counter.toString().padStart(12, '0')
        generated += id
        return id
    }
}
