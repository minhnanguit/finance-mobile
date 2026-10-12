package com.uit.finance.core.auth.pkce

/** SHA-256 của nền tảng. CSPRNG dùng chung ở `core/common` (`secureRandomBytes`). */
internal expect fun sha256(input: ByteArray): ByteArray
