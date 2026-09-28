package com.uit.finance.core.auth.pkce

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PkceGeneratorTest {

    // RFC 7636 Appendix B.
    private val rfcOctets = intArrayOf(
        116, 24, 223, 180, 151, 153, 224, 37, 79, 250, 96, 125, 216, 173, 187, 186,
        22, 212, 37, 77, 105, 214, 191, 240, 91, 88, 5, 88, 83, 132, 141, 121,
    ).map { it.toByte() }.toByteArray()

    @Test
    fun matchesTheRfc7636TestVector() {
        val pair = PkceGenerator(randomBytes = { rfcOctets }).pkce()

        assertEquals("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", pair.verifier)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pair.challenge)
    }

    @Test
    fun verifierUsesOnlyUnreservedCharactersAndMinimumLength() {
        val verifier = PkceGenerator().pkce().verifier

        assertEquals(43, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' }, "chỉ ký tự base64url, không padding")
    }

    @Test
    fun everyCallProducesFreshRandomness() {
        val generator = PkceGenerator()

        assertNotEquals(generator.pkce().verifier, generator.pkce().verifier)
        assertNotEquals(generator.state(), generator.state())
    }
}
