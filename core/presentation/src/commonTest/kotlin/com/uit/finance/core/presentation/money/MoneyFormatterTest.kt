package com.uit.finance.core.presentation.money

import com.uit.finance.core.common.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class MoneyFormatterTest {

    private val formatter = MoneyFormatter()

    @Test
    fun `VND không có phần lẻ - nhóm nghìn bằng dấu chấm`() {
        assertEquals(FormattedMoney("VND", "1.250.000", isNegative = false), formatter.format(Money(1_250_000, "VND")))
        assertEquals("VND 1.250.000", formatter.format(Money(1_250_000, "VND")).text)
        assertEquals("0", formatter.format(Money(0, "VND")).amount)
        assertEquals("999", formatter.format(Money(999, "VND")).amount)
        assertEquals("1.000", formatter.format(Money(1_000, "VND")).amount)
    }

    @Test
    fun `USD hai số lẻ - phần lẻ luôn đủ hai chữ số`() {
        assertEquals("95.300,50", formatter.format(Money(9_530_050, "USD")).amount)
        assertEquals("0,05", formatter.format(Money(5, "USD")).amount)
        assertEquals("12,00", formatter.format(Money(1_200, "USD")).amount)
    }

    @Test
    fun `số âm giữ dấu riêng - phần số không có dấu`() {
        val formatted = formatter.format(Money(-220_000, "VND"))
        assertEquals("220.000", formatted.amount)
        assertEquals(true, formatted.isNegative)
        assertEquals("-VND 220.000", formatted.text)
    }

    @Test
    fun `tiền tệ 0 - 3 - 4 số lẻ theo ISO 4217 và mã lạ mặc định 2`() {
        assertEquals("1.250", formatter.format(Money(1_250, "XPF")).amount)
        assertEquals("1.250", formatter.format(Money(1_250, "BIF")).amount)
        assertEquals("1,250", formatter.format(Money(1_250, "KWD")).amount)
        assertEquals("0,1250", formatter.format(Money(1_250, "CLF")).amount)
        assertEquals("1.000,00", formatter.format(Money(100_000, "XYZ")).amount)
    }

    @Test
    fun `đổi dấu phân cách theo quy ước khác`() {
        val english = MoneyFormatter(groupingSeparator = ',', decimalSeparator = '.')
        assertEquals("175,400.80", english.format(Money(17_540_080, "CNY")).amount)
    }

    @Test
    fun `formatCompact - rút gọn kiểu Việt Nam - một chữ số lẻ - bỏ phần lẻ 0`() {
        val formatter = MoneyFormatter()
        assertEquals("18,4tr", formatter.formatCompact(Money(18_400_000, "VND")))
        assertEquals("520k", formatter.formatCompact(Money(520_000, "VND")))
        assertEquals("1,5k", formatter.formatCompact(Money(1_500, "VND")))
        assertEquals("999", formatter.formatCompact(Money(999, "VND")))
        assertEquals("125tr", formatter.formatCompact(Money(125_300_000, "VND")))
        assertEquals("1,2 tỷ", formatter.formatCompact(Money(1_230_000_000, "VND")))
        assertEquals("-6tr", formatter.formatCompact(Money(-6_000_000, "VND")))
        assertEquals("0", formatter.formatCompact(Money(0, "VND")))
    }

    @Test
    fun `formatCompact - chọn đơn vị sau khi làm tròn - không ra 1000k`() {
        val formatter = MoneyFormatter()
        assertEquals("1tr", formatter.formatCompact(Money(999_950, "VND")))
        assertEquals("1K", formatter.formatCompact(Money(999_60, "USD")))
        assertEquals("1 tỷ", formatter.formatCompact(Money(999_960_000, "VND")))
        assertEquals("999k", formatter.formatCompact(Money(999_400, "VND")))
        assertEquals("100k", formatter.formatCompact(Money(99_960, "VND")))
    }

    @Test
    fun `formatCompact - tiền tệ có phần lẻ tính theo đơn vị chính - dùng K M B`() {
        val formatter = MoneyFormatter()
        assertEquals("1,2K", formatter.formatCompact(Money(1_234_56, "USD")))
        assertEquals("12,5", formatter.formatCompact(Money(12_50, "USD")))
    }
}
