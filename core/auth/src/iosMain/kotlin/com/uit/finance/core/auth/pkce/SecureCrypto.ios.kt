@file:OptIn(ExperimentalForeignApi::class)

package com.uit.finance.core.auth.pkce

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

internal actual fun sha256(input: ByteArray): ByteArray {
    require(input.isNotEmpty()) { "input không được rỗng" }
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { source ->
        digest.usePinned { target ->
            CC_SHA256(source.addressOf(0), input.size.convert(), target.addressOf(0).reinterpret())
        }
    }
    return digest
}
