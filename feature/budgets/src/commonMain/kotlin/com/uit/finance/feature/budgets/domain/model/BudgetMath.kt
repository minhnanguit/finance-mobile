package com.uit.finance.feature.budgets.domain.model

import com.uit.finance.core.common.money.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/** Phép tính của ADR-007 G9–G11, tách riêng để test bằng số. */
internal object BudgetMath {

    const val MAX_PERCENT = 999

    /** So bằng số nguyên, không qua %: không tràn số, không sai số làm tròn (G9). */
    fun tone(spent: Long, amount: Long): BudgetTone = when {
        spent >= amount -> BudgetTone.Over
        spent >= amount - amount / 5 -> BudgetTone.Warning
        else -> BudgetTone.Normal
    }

    /**
     * Chỉ để hiển thị: làm tròn xuống, kẹp 0..999 (G10). Chia nguyên cho chính xác: `2_300_000.0 / 2_000_000 * 100`
     * ra `114.999…` nên số thực làm tròn xuống thành 114 thay vì 115. Chỉ khi `spent * 100` có thể tràn Long mới
     * dùng số thực; vùng đó đã kẹp ở 999.
     */
    fun percent(spent: Long, amount: Long): Int {
        val raw = if (spent <= Long.MAX_VALUE / 100) spent * 100 / amount else (spent.toDouble() / amount * 100).toLong()
        return raw.coerceIn(0, MAX_PERCENT.toLong()).toInt()
    }

    fun ratio(spent: Long, amount: Long): Double = spent.toDouble() / amount

    /**
     * Mỗi ngày còn được chi = còn lại / số ngày còn lại tính cả hôm nay, làm tròn xuống. Chỉ có ở tháng hiện tại và
     * khi còn lại > 0 (G11).
     */
    fun dailyAllowance(remaining: Money, month: YearMonth, today: LocalDate): Money? {
        if (today.yearMonth != month || remaining.amountMinor <= 0) return null
        val daysLeft = month.lastDay.day - today.day + 1
        return Money(remaining.amountMinor / daysLeft, remaining.currency)
    }

    fun saturatingPlus(a: Long, b: Long): Long {
        val sum = a + b
        return when {
            a > 0 && b > 0 && sum < 0 -> Long.MAX_VALUE
            a < 0 && b < 0 && sum >= 0 -> Long.MIN_VALUE
            else -> sum
        }
    }

    /** [subtrahend] là số đã chi, luôn ≥ 0, nên đổi dấu không tràn. */
    fun saturatingMinus(value: Long, subtrahend: Long): Long = saturatingPlus(value, -subtrahend)
}
