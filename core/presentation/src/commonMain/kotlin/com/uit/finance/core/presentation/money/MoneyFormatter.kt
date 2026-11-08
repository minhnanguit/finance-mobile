package com.uit.finance.core.presentation.money

import com.uit.finance.core.common.money.Currencies
import com.uit.finance.core.common.money.Money

/** Số tiền đã tách phần để màn hình tự xếp kiểu (mã tiền tệ nhỏ, số đậm). */
data class FormattedMoney(
    val currency: String,
    /** Chỉ chữ số và dấu phân cách, không dấu âm: `1.250.000`, `95.300,50`. */
    val amount: String,
    val isNegative: Boolean,
) {
    /** `VND 1.250.000` · `-USD 95.300,50`. */
    val text: String
        get() = buildString {
            if (isNegative) append('-')
            append(currency).append(' ').append(amount)
        }
}

/**
 * Format [Money] cho màn hình theo quy ước Việt Nam (nhóm nghìn ".", phần lẻ ","). Thuần Kotlin để
 * Android và iOS hiện giống hệt nhau; tiền tệ hiện bằng mã ISO vì app nhiều tiền tệ. Mọi chỗ hiện tiền
 * đi qua đây, không tự ghép chuỗi.
 */
class MoneyFormatter(
    private val groupingSeparator: Char = NumberSymbols.GROUPING,
    private val decimalSeparator: Char = NumberSymbols.DECIMAL,
) {
    fun format(money: Money): FormattedMoney {
        val digits = Currencies.minorDigits(money.currency)
        val isNegative = money.amountMinor < 0
        // Số tiền bị giới hạn 10^15 (LEDGER-PLAN B4) nên không chạm Long.MIN_VALUE.
        val absolute = if (isNegative) -money.amountMinor else money.amountMinor
        var scale = 1L
        repeat(digits) { scale *= 10 }
        val whole = absolute / scale
        val fraction = absolute % scale
        val amount = buildString {
            append(groupThousands(whole))
            if (digits > 0) {
                append(decimalSeparator)
                append(fraction.toString().padStart(digits, '0'))
            }
        }
        return FormattedMoney(currency = money.currency, amount = amount, isNegative = isNegative)
    }

    /**
     * Số rút gọn cho trục biểu đồ và giữa biểu đồ tròn (docs/REPORTS-PLAN.md V6): `18,4tr`, `520k`, `1,2 tỷ`. Không kèm
     * mã tiền tệ; tiền tệ khác VND dùng `K/M/B`. Một chữ số lẻ khi dưới 100 đơn vị, bỏ `,0`.
     */
    fun formatCompact(money: Money): String {
        var scale = 1.0
        repeat(Currencies.minorDigits(money.currency)) { scale *= 10 }
        val major = money.amountMinor / scale
        val absolute = kotlin.math.abs(major)
        val vnd = money.currency == "VND"
        val units = listOf(1.0 to "", 1e3 to (if (vnd) "k" else "K"), 1e6 to (if (vnd) "tr" else "M"), 1e9 to (if (vnd) " tỷ" else "B"))
        var index = units.indexOfLast { absolute >= it.first }.coerceAtLeast(0)
        // Chọn đơn vị theo số SAU khi làm tròn: 999.950 → "1tr", không phải "1.000k".
        while (index < units.lastIndex && roundedCompact(absolute / units[index].first) >= 1000.0) index++
        val (unit, suffix) = units[index]
        val value = absolute / unit
        val tenths = kotlin.math.round(value * 10).toLong()
        val number = if (value >= 100 || tenths % 10 == 0L) {
            groupThousands(kotlin.math.round(value).toLong())
        } else {
            "${tenths / 10}$decimalSeparator${tenths % 10}"
        }
        return (if (major < 0) "-" else "") + number + suffix
    }

    /** Giá trị [formatCompact] sẽ in: một chữ số lẻ dưới 100, số nguyên từ 100. */
    private fun roundedCompact(value: Double): Double =
        if (value >= 100) kotlin.math.round(value) else kotlin.math.round(value * 10) / 10

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        return buildString {
            digits.forEachIndexed { index, char ->
                if (index > 0 && (digits.length - index) % 3 == 0) append(groupingSeparator)
                append(char)
            }
        }
    }
}
