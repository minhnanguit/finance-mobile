package com.uit.finance.core.common.security

import java.security.SecureRandom

private val secureRandom = SecureRandom()

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "size phải > 0" }
    return ByteArray(size).also(secureRandom::nextBytes)
}
