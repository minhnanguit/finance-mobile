package com.uit.finance.core.common.id

/**
 * UUID name-based SHA-1 (version 5, RFC 4122 §4.3). Kotlin thuần để Android và iOS ra cùng một kết quả: dùng cho id
 * giao dịch của từng kỳ định kỳ (ADR-008 §2), mọi máy tính ra cùng id cho cùng (khoản, ngày).
 *
 * @param namespace UUID dạng chuỗi chuẩn
 * @return UUID dạng chuỗi chuẩn, chữ thường
 */
fun uuidV5(namespace: String, name: String): String {
    val nsBytes = uuidBytes(namespace)
    val hash = sha1(nsBytes + name.encodeToByteArray())
    hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
    hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
    return formatUuid(hash.copyOf(UUID_BYTES))
}

private const val UUID_BYTES = 16
private const val HEX = "0123456789abcdef"

private fun uuidBytes(uuid: String): ByteArray {
    val hex = uuid.replace("-", "")
    require(hex.length == UUID_BYTES * 2) { "not a UUID" }
    return ByteArray(UUID_BYTES) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

private fun formatUuid(bytes: ByteArray): String = buildString(36) {
    bytes.forEachIndexed { i, byte ->
        if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
        val v = byte.toInt() and 0xff
        append(HEX[v shr 4])
        append(HEX[v and 0x0f])
    }
}

/** SHA-1 (FIPS 180-4). Chỉ dùng làm hàm băm cho UUIDv5, không dùng cho bảo mật. */
internal fun sha1(message: ByteArray): ByteArray {
    var h0 = 0x67452301
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476
    var h4 = 0xC3D2E1F0.toInt()

    val bitLength = message.size.toLong() * 8
    val paddedLength = ((message.size + 9 + 63) / 64) * 64
    val padded = message.copyOf(paddedLength)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[paddedLength - 1 - i] = (bitLength ushr (8 * i)).toByte()
    }

    val w = IntArray(80)
    for (chunk in 0 until paddedLength / 64) {
        val base = chunk * 64
        for (i in 0 until 16) {
            w[i] = ((padded[base + 4 * i].toInt() and 0xff) shl 24) or
                ((padded[base + 4 * i + 1].toInt() and 0xff) shl 16) or
                ((padded[base + 4 * i + 2].toInt() and 0xff) shl 8) or
                (padded[base + 4 * i + 3].toInt() and 0xff)
        }
        for (i in 16 until 80) {
            w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
        }
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        for (i in 0 until 80) {
            val (f, k) = when (i) {
                in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                else -> (b xor c xor d) to 0xCA62C1D6.toInt()
            }
            val temp = a.rotateLeft(5) + f + e + k + w[i]
            e = d
            d = c
            c = b.rotateLeft(30)
            b = a
            a = temp
        }
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
    }

    val out = ByteArray(20)
    intArrayOf(h0, h1, h2, h3, h4).forEachIndexed { i, h ->
        out[4 * i] = (h ushr 24).toByte()
        out[4 * i + 1] = (h ushr 16).toByte()
        out[4 * i + 2] = (h ushr 8).toByte()
        out[4 * i + 3] = h.toByte()
    }
    return out
}
