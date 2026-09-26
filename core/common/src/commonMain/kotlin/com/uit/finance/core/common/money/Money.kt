package com.uit.finance.core.common.money

/**
 * Monetary amount in minor units (cents, đồng, ...) with an ISO-4217 currency code.
 * Floating point is forbidden by the architecture (rule #9), so arithmetic is `Long` only and
 * only between equal currencies.
 */
data class Money(val amountMinor: Long, val currency: String) : Comparable<Money> {

    init {
        require(currency.length == 3 && currency.all { it in 'A'..'Z' }) {
            "currency must be a 3-letter upper-case ISO-4217 code, was '$currency'"
        }
    }

    val isZero: Boolean get() = amountMinor == 0L
    val isNegative: Boolean get() = amountMinor < 0L
    val isPositive: Boolean get() = amountMinor > 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = Math.addExact(amountMinor, other.amountMinor))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = Math.subtractExact(amountMinor, other.amountMinor))
    }

    operator fun unaryMinus(): Money = copy(amountMinor = Math.negateExact(amountMinor))

    operator fun times(factor: Long): Money = copy(amountMinor = Math.multiplyExact(amountMinor, factor))

    operator fun times(factor: Int): Money = times(factor.toLong())

    fun abs(): Money = if (isNegative) -this else this

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    /**
     * Splits the amount into [parts] shares whose sum equals this amount; the remainder
     * is distributed one minor unit at a time to the first shares (no money is lost).
     */
    fun allocate(parts: Int): List<Money> {
        require(parts > 0) { "parts must be > 0" }
        val base = amountMinor / parts
        val remainder = amountMinor % parts
        return List(parts) { index ->
            copy(amountMinor = base + if (index < kotlin.math.abs(remainder)) remainder.sign() else 0L)
        }
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: $currency vs ${other.currency}"
        }
    }

    override fun toString(): String = "$amountMinor $currency"

    companion object {
        fun zero(currency: String): Money = Money(0L, currency)
    }
}

private fun Long.sign(): Long = if (this < 0) -1L else 1L

fun Iterable<Money>.sum(currency: String): Money =
    fold(Money.zero(currency)) { acc, money -> acc + money }

/** Same-currency Math helpers without pulling `java.lang.Math` into common code. */
private object Math {
    fun addExact(a: Long, b: Long): Long {
        val r = a + b
        if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("long overflow")
        return r
    }

    fun subtractExact(a: Long, b: Long): Long {
        val r = a - b
        if (((a xor b) and (a xor r)) < 0) throw ArithmeticException("long overflow")
        return r
    }

    fun negateExact(a: Long): Long {
        if (a == Long.MIN_VALUE) throw ArithmeticException("long overflow")
        return -a
    }

    fun multiplyExact(a: Long, b: Long): Long {
        val r = a * b
        val aa = kotlin.math.abs(a)
        val ab = kotlin.math.abs(b)
        if ((aa or ab) ushr 31 != 0L) {
            if ((b != 0L && r / b != a) || (a == Long.MIN_VALUE && b == -1L)) {
                throw ArithmeticException("long overflow")
            }
        }
        return r
    }
}
