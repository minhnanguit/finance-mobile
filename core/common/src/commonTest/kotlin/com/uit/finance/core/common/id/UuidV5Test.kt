package com.uit.finance.core.common.id

import kotlin.test.Test
import kotlin.test.assertEquals

/** Vector của RFC 4122 và vector chung ADR-009 §2: backend có test Java với đúng các giá trị này. */
class UuidV5Test {

    private val dns = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    private val recurringNamespace = "9ec1e9e3-0bb0-485a-ba55-77182d53d5f4"
    private val recurringId = "3f1c2b9e-7a4d-4e8b-9c21-5d6e7f8a9b0c"

    @Test
    fun `khớp vector RFC 4122`() {
        assertEquals("2ed6657d-e927-568b-95e1-2665a8aea6a2", uuidV5(dns, "www.example.com"))
    }

    @Test
    fun `khớp vector chung của ADR-009`() {
        assertEquals("0cdf7899-a9f2-53ca-b1c4-f9475ebfe6a0", uuidV5(recurringNamespace, "$recurringId/2026-12-05"))
        assertEquals("b62a0a6d-780d-538f-9512-a0d2e7fc9cea", uuidV5(recurringNamespace, "$recurringId/2027-02-28"))
        assertEquals("bee84904-e343-5093-9109-a21e2e728584", uuidV5(recurringNamespace, "$recurringId/2028-02-29"))
    }

    @Test
    fun `SHA-1 khớp vector FIPS - chuỗi rỗng và abc và chuỗi dài nhiều khối`() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", sha1(ByteArray(0)).toHex())
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1("abc".encodeToByteArray()).toHex())
        assertEquals(
            "84983e441c3bd26ebaae4aa1f95129e5e54670f1",
            sha1("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).toHex(),
        )
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
