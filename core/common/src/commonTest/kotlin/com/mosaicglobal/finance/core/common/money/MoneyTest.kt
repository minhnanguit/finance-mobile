package com.mosaicglobal.finance.core.common.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {

    @Test
    fun addsSameCurrency() {
        val sum = Money(1_050, "VND") + Money(950, "VND")
        assertEquals(Money(2_000, "VND"), sum)
    }

    @Test
    fun subtractsAndGoesNegative() {
        val diff = Money(500, "USD") - Money(750, "USD")
        assertEquals(Money(-250, "USD"), diff)
        assertTrue(diff.isNegative)
    }

    @Test
    fun rejectsCurrencyMismatch() {
        assertFailsWith<IllegalArgumentException> { Money(1, "USD") + Money(1, "EUR") }
        assertFailsWith<IllegalArgumentException> { Money(1, "USD") - Money(1, "EUR") }
        assertFailsWith<IllegalArgumentException> { Money(1, "USD") < Money(1, "EUR") }
    }

    @Test
    fun rejectsInvalidCurrencyCode() {
        assertFailsWith<IllegalArgumentException> { Money(1, "usd") }
        assertFailsWith<IllegalArgumentException> { Money(1, "US") }
        assertFailsWith<IllegalArgumentException> { Money(1, "") }
    }

    @Test
    fun multipliesByIntegerFactorOnly() {
        assertEquals(Money(300, "EUR"), Money(100, "EUR") * 3)
        assertEquals(Money(-300, "EUR"), Money(100, "EUR") * -3L)
    }

    @Test
    fun detectsOverflow() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, "USD") + Money(1, "USD") }
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE, "USD") * 2 }
        assertFailsWith<ArithmeticException> { -Money(Long.MIN_VALUE, "USD") }
    }

    @Test
    fun comparesWithinCurrency() {
        assertTrue(Money(100, "USD") < Money(200, "USD"))
        assertTrue(Money(200, "USD") >= Money(200, "USD"))
    }

    @Test
    fun allocatesWithoutLosingMinorUnits() {
        val shares = Money(100, "USD").allocate(3)
        assertEquals(listOf(Money(34, "USD"), Money(33, "USD"), Money(33, "USD")), shares)
        assertEquals(Money(100, "USD"), shares.sum("USD"))

        val negative = Money(-100, "USD").allocate(3)
        assertEquals(Money(-100, "USD"), negative.sum("USD"))
    }

    @Test
    fun sumsCollections() {
        val total = listOf(Money(1, "VND"), Money(2, "VND"), Money(3, "VND")).sum("VND")
        assertEquals(Money(6, "VND"), total)
        assertEquals(Money.zero("VND"), emptyList<Money>().sum("VND"))
    }
}
