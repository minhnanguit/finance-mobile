@file:OptIn(ExperimentalForeignApi::class)

package com.uit.finance.core.common.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "size phải > 0" }
    val bytes = ByteArray(size)
    val status = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.convert(), it.addressOf(0)) }
    check(status == errSecSuccess) { "SecRandomCopyBytes thất bại: $status" }
    return bytes
}
