package com.uit.finance.feature.budgets.domain

import com.uit.finance.core.common.money.Money
import com.uit.finance.feature.budgets.domain.model.BudgetMath
import com.uit.finance.feature.budgets.domain.model.BudgetTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class BudgetMathTest {

    @Test
    fun `màu đổi đúng ngưỡng 80 và 100 phần trăm bằng số nguyên`() {
        assertEquals(BudgetTone.Normal, BudgetMath.tone(spent = 799, amount = 1_000))
        assertEquals(BudgetTone.Warning, BudgetMath.tone(spent = 800, amount = 1_000))
        assertEquals(BudgetTone.Warning, BudgetMath.tone(spent = 999, amount = 1_000))
        assertEquals(BudgetTone.Over, BudgetMath.tone(spent = 1_000, amount = 1_000))
        assertEquals(BudgetTone.Over, BudgetMath.tone(spent = 2_300_000, amount = 2_000_000))
    }

    @Test
    fun `màu không tràn số ở biên 10 mũ 15 và khi đã chi kẹt ở biên Long`() {
        val max = 1_000_000_000_000_000L
        assertEquals(BudgetTone.Warning, BudgetMath.tone(spent = max - max / 5, amount = max))
        assertEquals(BudgetTone.Over, BudgetMath.tone(spent = Long.MAX_VALUE, amount = max))
    }

    @Test
    fun `phần trăm làm tròn xuống và kẹp ở 999`() {
        assertEquals(85, BudgetMath.percent(spent = 2_550_000, amount = 3_000_000))
        assertEquals(83, BudgetMath.percent(spent = 2_500_000, amount = 3_000_000))
        assertEquals(115, BudgetMath.percent(spent = 2_300_000, amount = 2_000_000))
        assertEquals(999, BudgetMath.percent(spent = 50_000, amount = 1))
        assertEquals(999, BudgetMath.percent(spent = Long.MAX_VALUE, amount = 1))
        assertEquals(0, BudgetMath.percent(spent = 0, amount = 1_000))
    }

    @Test
    fun `mỗi ngày còn được chi chia cho số ngày còn lại tính cả hôm nay`() {
        val october = YearMonth(2026, 10)
        // 17 tới 31 tháng 10 là 15 ngày.
        assertEquals(Money(30_000, "VND"), BudgetMath.dailyAllowance(Money(450_000, "VND"), october, LocalDate(2026, 10, 17)))
        // Ngày cuối tháng: cả phần còn lại.
        assertEquals(Money(450_000, "VND"), BudgetMath.dailyAllowance(Money(450_000, "VND"), october, LocalDate(2026, 10, 31)))
        // Làm tròn xuống: 8000 cent chia 18 ngày.
        assertEquals(Money(444, "USD"), BudgetMath.dailyAllowance(Money(8_000, "USD"), october, LocalDate(2026, 10, 14)))
    }

    @Test
    fun `mỗi ngày còn được chi chỉ có ở tháng hiện tại khi còn lại lớn hơn 0`() {
        val october = YearMonth(2026, 10)
        assertNull(BudgetMath.dailyAllowance(Money(450_000, "VND"), october, LocalDate(2026, 11, 2)))
        assertNull(BudgetMath.dailyAllowance(Money(0, "VND"), october, LocalDate(2026, 10, 17)))
        assertNull(BudgetMath.dailyAllowance(Money(-300_000, "VND"), october, LocalDate(2026, 10, 17)))
    }

    @Test
    fun `cộng bão hoà kẹt ở biên Long`() {
        assertEquals(Long.MAX_VALUE, BudgetMath.saturatingPlus(Long.MAX_VALUE, 1))
        assertEquals(Long.MIN_VALUE, BudgetMath.saturatingMinus(-1, Long.MAX_VALUE))
        assertEquals(-300_000L, BudgetMath.saturatingMinus(2_000_000, 2_300_000))
    }
}
