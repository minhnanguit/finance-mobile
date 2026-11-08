package com.uit.finance.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTextTest {

    @Test
    fun `mọi nguyên âm có dấu - hoa lẫn thường - về chữ gốc`() {
        val vowels = mapOf(
            'a' to "aàáảãạăằắẳẵặâầấẩẫậ",
            'e' to "eèéẻẽẹêềếểễệ",
            'i' to "iìíỉĩị",
            'o' to "oòóỏõọôồốổỗộơờớởỡợ",
            'u' to "uùúủũụưừứửữự",
            'y' to "yỳýỷỹỵ",
        )
        vowels.forEach { (base, variants) ->
            assertEquals(base.toString().repeat(variants.length), variants.foldForSearch(), "thường: $base")
            assertEquals(base.toString().repeat(variants.length), variants.uppercase().foldForSearch(), "hoa: $base")
        }
    }

    @Test
    fun `đ và Đ thành d`() {
        assertEquals("di dau do", "Đi đâu ĐÓ".foldForSearch())
    }

    @Test
    fun `cùng một chữ ở dạng NFC và NFD ra cùng kết quả`() {
        val nfc = "Cà phê Phượng Đỗ"
        // NFD: chữ gốc + dấu tổ hợp. "ượ" = u + U+031B (móc), o + U+031B + U+0323 (nặng); "ỗ" = o + U+0302 + U+0303.
        // "Đ" không có dạng tổ hợp, NFD vẫn là chính nó.
        val nfd = "Ca\u0300 phe\u0302 Phu\u031Bo\u031B\u0323ng Đo\u0302\u0303"
        // Trộn hai dạng: chữ dựng sẵn "ê" cộng thêm dấu sắc tổ hợp.
        val mixed = "Cà phê\u0301"

        assertEquals("ca phe phuong do", nfc.foldForSearch())
        assertEquals(nfc.foldForSearch(), nfd.foldForSearch())
        assertEquals("ca phe", mixed.foldForSearch())
    }

    @Test
    fun `gộp khoảng trắng và bỏ khoảng trắng hai đầu`() {
        assertEquals("ca phe highlands", "  Cà\tphê \n\n HIGHLANDS  ".foldForSearch())
    }

    @Test
    fun `chuỗi rỗng và chuỗi chỉ có khoảng trắng hoặc dấu thành rỗng`() {
        assertEquals("", "".foldForSearch())
        assertEquals("", "   \t".foldForSearch())
        assertEquals("", "́̀".foldForSearch())
    }

    @Test
    fun `emoji và ký hiệu giữ nguyên`() {
        assertEquals("ca phe ☕️ 🎉 giam 50% a_b", "Cà phê ☕️ 🎉 Giảm 50% a_b".foldForSearch())
    }

    @Test
    fun `searchTextOf ghép các phần và bỏ phần null`() {
        assertEquals("highlands ca phe sang", searchTextOf("Highlands", "Cà phê sáng"))
        assertEquals("ca phe", searchTextOf(null, "Cà phê"))
        assertEquals("", searchTextOf(null, null))
    }
}
