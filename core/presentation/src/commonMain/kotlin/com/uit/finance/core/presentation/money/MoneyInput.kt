package com.uit.finance.core.presentation.money

import com.uit.finance.core.common.money.Currencies

/**
 * Chuỗi người dùng gõ vào ô số tiền ↔ `amountMinor`. Ô nhập chỉ giữ chữ số, tối đa một dấu phần lẻ
 * ([NumberSymbols.DECIMAL]) và (khi cho phép) dấu "-" ở đầu; dấu nhóm nghìn chỉ là hiệu ứng hiển thị,
 * không nằm trong chuỗi. Không dùng số thực: phép đổi làm trên chữ số nên không sai một đồng nào.
 */
object MoneyInput {

    /** 10¹⁸ vẫn nằm trong Long; giới hạn nghiệp vụ (10¹⁵) do `LedgerRules` kiểm, ở đây chỉ chặn tràn. */
    private const val MAX_WHOLE_DIGITS = 16

    /**
     * Làm sạch [raw] theo luật trên: bỏ ký tự lạ, chấp nhận cả "." lẫn "," làm dấu phần lẻ (bàn phím số của
     * từng máy khác nhau) khi tiền tệ có phần lẻ, cắt phần lẻ theo số chữ số của [currency], bỏ số 0 thừa ở đầu.
     */
    fun sanitize(raw: String, currency: String, allowNegative: Boolean = false): String {
        val digits = Currencies.minorDigits(currency)
        val negative = allowNegative && raw.trimStart().startsWith('-')
        val whole = StringBuilder()
        val fraction = StringBuilder()
        var inFraction = false
        for (char in raw) {
            when {
                char.isDigit() && !inFraction -> if (whole.length < MAX_WHOLE_DIGITS) whole.append(char)
                char.isDigit() -> if (fraction.length < digits) fraction.append(char)
                (char == NumberSymbols.DECIMAL || char == '.') && digits > 0 && !inFraction -> inFraction = true
            }
        }
        val trimmedWhole = whole.toString().trimStart('0').ifEmpty { if (inFraction || whole.isNotEmpty()) "0" else "" }
        return buildString {
            if (negative) append('-')
            append(trimmedWhole)
            if (inFraction) append(NumberSymbols.DECIMAL).append(fraction)
        }
    }

    /** `null` khi chưa có chữ số nào. "12,5" USD → 1250. */
    fun toMinor(text: String, currency: String): Long? {
        val digits = Currencies.minorDigits(currency)
        val negative = text.startsWith('-')
        val body = text.removePrefix("-")
        val whole = body.substringBefore(NumberSymbols.DECIMAL)
        val fraction = body.substringAfter(NumberSymbols.DECIMAL, missingDelimiterValue = "")
        if (whole.isEmpty() && fraction.isEmpty()) return null
        if (!whole.all(Char::isDigit) || !fraction.all(Char::isDigit) || fraction.length > digits) return null
        val minorText = whole.ifEmpty { "0" } + fraction.padEnd(digits, '0')
        val value = minorText.trimStart('0').ifEmpty { "0" }.toLongOrNull() ?: return null
        return if (negative) -value else value
    }

    /** Ngược lại của [toMinor], để điền sẵn khi sửa: 1250 USD → "12,5"; 50000 VND → "50000". */
    fun fromMinor(minor: Long, currency: String): String {
        val digits = Currencies.minorDigits(currency)
        val negative = minor < 0
        val absolute = if (negative) -minor else minor
        val text = absolute.toString().padStart(digits + 1, '0')
        val whole = text.dropLast(digits)
        val fraction = text.takeLast(digits).trimEnd('0')
        return buildString {
            if (negative) append('-')
            append(whole)
            if (fraction.isNotEmpty()) append(NumberSymbols.DECIMAL).append(fraction)
        }
    }

    /**
     * Đổi tiền tệ (chọn ví khác loại) thì giữ phần nguyên, cắt phần lẻ cho hợp tiền tệ mới. [text] là chuỗi
     * đã qua [sanitize] với tiền tệ cũ.
     */
    fun convert(text: String, currency: String, allowNegative: Boolean = false): String {
        val digits = Currencies.minorDigits(currency)
        val whole = text.substringBefore(NumberSymbols.DECIMAL)
        val fraction = text.substringAfter(NumberSymbols.DECIMAL, missingDelimiterValue = "")
        val kept = if (digits > 0 && text.contains(NumberSymbols.DECIMAL)) whole + NumberSymbols.DECIMAL + fraction.take(digits) else whole
        return sanitize(kept, currency, allowNegative)
    }
}
