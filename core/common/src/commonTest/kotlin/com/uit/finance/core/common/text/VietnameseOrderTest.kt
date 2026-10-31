package com.uit.finance.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals

class VietnameseOrderTest {

    @Test
    fun `danh mục mặc định xếp theo chữ cái tiếng Việt`() {
        val names = listOf(
            "Ăn uống", "Sức khoẻ", "Phí giao dịch", "Nhà cửa", "Mua sắm", "Hoá đơn", "Giải trí", "Giáo dục",
            "Gia đình", "Di chuyển", "Chi khác", "Đi lại",
        )

        assertEquals(
            listOf(
                "Ăn uống", "Chi khác", "Di chuyển", "Đi lại", "Gia đình", "Giải trí", "Giáo dục", "Hoá đơn",
                "Mua sắm", "Nhà cửa", "Phí giao dịch", "Sức khoẻ",
            ),
            names.sortedWith(VietnameseOrder),
        )
    }

    @Test
    fun `nguyên âm có mũ và móc đứng sau nguyên âm gốc`() {
        assertEquals(
            listOf("an", "ăn", "ân", "bo", "bô", "bơ", "du", "đu", "tu", "tư"),
            listOf("tư", "bơ", "ân", "đu", "an", "bô", "ăn", "du", "tu", "bo").sortedWith(VietnameseOrder),
        )
    }

    @Test
    fun `chữ giống nhau thì xét thanh - không phân biệt hoa thường`() {
        assertEquals(listOf("ma", "mà", "má", "mả", "mã", "mạ"), listOf("mạ", "má", "ma", "mã", "mà", "mả").sortedWith(VietnameseOrder))
        assertEquals(0, VietnameseOrder.compare("Ăn uống", "Ăn uống"))
        assertEquals(true, VietnameseOrder.compare("ăn", "Bò") < 0)
    }

    @Test
    fun `dấu cách và số đứng trước chữ - chuỗi ngắn đứng trước`() {
        assertEquals(listOf("An", "An 2", "Anh", "Ăn"), listOf("Ăn", "Anh", "An 2", "An").sortedWith(VietnameseOrder))
    }
}
