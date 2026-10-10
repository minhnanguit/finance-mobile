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
}
