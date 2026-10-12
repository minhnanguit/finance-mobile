package com.uit.finance.core.presentation.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class MoneyInputTest {

    @Test
    fun `VND chỉ giữ chữ số và bỏ số 0 thừa ở đầu`() {
        assertEquals("50000", MoneyInput.sanitize("050.000đ", "VND"))
        assertEquals("", MoneyInput.sanitize("abc", "VND"))
        assertEquals("0", MoneyInput.sanitize("000", "VND"))
        assertEquals("12", MoneyInput.sanitize("1,2", "VND"))
    }

    @Test
    fun `USD nhận dấu chấm hoặc phẩy làm phần lẻ và cắt còn 2 chữ số`() {
        assertEquals("12,5", MoneyInput.sanitize("12.5", "USD"))
        assertEquals("12,59", MoneyInput.sanitize("12,599", "USD"))
        assertEquals("0,5", MoneyInput.sanitize(",5", "USD"))
        assertEquals("1,23", MoneyInput.sanitize("1,2,3", "USD"))
    }

    @Test
    fun `dấu âm chỉ giữ khi được phép`() {
        assertEquals("-200", MoneyInput.sanitize("-200", "VND", allowNegative = true))
        assertEquals("200", MoneyInput.sanitize("-200", "VND"))
    }

    @Test
    fun `đổi sang minor theo số chữ số của tiền tệ`() {
        assertEquals(50_000L, MoneyInput.toMinor("50000", "VND"))
        assertEquals(1_250L, MoneyInput.toMinor("12,5", "USD"))
        assertEquals(1_200L, MoneyInput.toMinor("12", "USD"))
        assertEquals(50L, MoneyInput.toMinor("0,5", "USD"))
        assertEquals(-200L, MoneyInput.toMinor("-200", "VND"))
        assertNull(MoneyInput.toMinor("", "VND"))
        assertNull(MoneyInput.toMinor("-", "VND"))
    }

    @Test
    fun `minor ngược lại thành chuỗi để sửa`() {
        assertEquals("12,5", MoneyInput.fromMinor(1_250, "USD"))
        assertEquals("0,05", MoneyInput.fromMinor(5, "USD"))
        assertEquals("12", MoneyInput.fromMinor(1_200, "USD"))
        assertEquals("50000", MoneyInput.fromMinor(50_000, "VND"))
        assertEquals("-1", MoneyInput.fromMinor(-100, "USD"))
    }

    @Test
    fun `đổi tiền tệ thì cắt phần lẻ cho hợp tiền tệ mới`() {
        assertEquals("12", MoneyInput.convert("12,5", "VND"))
        assertEquals("1,250", MoneyInput.convert("1,2509", "KWD"))
    }
}
