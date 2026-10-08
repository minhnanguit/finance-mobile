package com.uit.finance.core.common.security

/**
 * Byte ngẫu nhiên từ CSPRNG của hệ điều hành (Android `SecureRandom`, iOS `SecRandomCopyBytes`).
 * Dùng cho mọi thứ cần bí mật: PKCE, `state`, khoá mã hoá DB. `kotlin.random.Random` KHÔNG đủ an toàn.
 */
expect fun secureRandomBytes(size: Int): ByteArray

/** Hex chữ thường, 2 ký tự mỗi byte. */
fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
}
