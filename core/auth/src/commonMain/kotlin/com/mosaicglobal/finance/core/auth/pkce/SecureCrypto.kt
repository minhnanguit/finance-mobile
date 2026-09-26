package com.mosaicglobal.finance.core.auth.pkce

/** CSPRNG của nền tảng. `kotlin.random.Random` KHÔNG đủ an toàn cho PKCE / state. */
internal expect fun secureRandomBytes(size: Int): ByteArray

internal expect fun sha256(input: ByteArray): ByteArray
