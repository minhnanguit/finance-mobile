package com.mosaicglobal.finance.core.auth.pkce

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal data class PkcePair(val verifier: String, val challenge: String)

/** PKCE S256 theo RFC 7636, cùng giá trị `state` chống CSRF. */
internal class PkceGenerator(
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
    private val digest: (ByteArray) -> ByteArray = ::sha256,
) {
    /** 32 byte ngẫu nhiên → verifier 43 ký tự, đúng mức tối thiểu RFC 7636 yêu cầu. */
    fun pkce(): PkcePair {
        val verifier = base64Url(randomBytes(VERIFIER_BYTES))
        return PkcePair(verifier = verifier, challenge = challengeFor(verifier))
    }

    fun challengeFor(verifier: String): String = base64Url(digest(verifier.encodeToByteArray()))

    fun state(): String = base64Url(randomBytes(STATE_BYTES))

    private companion object {
        const val VERIFIER_BYTES = 32
        const val STATE_BYTES = 16
    }
}

@OptIn(ExperimentalEncodingApi::class)
private val base64UrlNoPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

@OptIn(ExperimentalEncodingApi::class)
private fun base64Url(bytes: ByteArray): String = base64UrlNoPadding.encode(bytes)
