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
    private val groupingSeparator: Char = '.',
    private val decimalSeparator: Char = ',',
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
