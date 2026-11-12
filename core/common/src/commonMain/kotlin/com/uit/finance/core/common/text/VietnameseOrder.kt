package com.uit.finance.core.common.text

/**
 * So sánh chuỗi theo thứ tự chữ cái tiếng Việt: a ă â b c d đ e ê … o ô ơ … u ư … y, không phân biệt hoa thường.
 * Dấu thanh chỉ dùng để phân định khi phần chữ giống hệt nhau (không dấu → huyền → sắc → hỏi → ngã → nặng).
 *
 * Cần vì so theo mã Unicode xếp "Ăn uống" sau "Sức khoẻ" ('ă' là U+0103, lớn hơn 'z'), và `commonMain` không có
 * `Collator`. Ký tự không thuộc bảng (số, dấu cách, emoji…) giữ thứ tự mã của nó: chữ số và dấu cách đứng trước
 * chữ cái, ký tự lạ đứng sau.
 */
object VietnameseOrder : Comparator<String> {

    private const val LETTER_BASE = 1_000
    private const val OTHER_BASE = 100_000

    /** Mỗi nhóm: chữ gốc rồi 5 thanh (huyền, sắc, hỏi, ngã, nặng). Phụ âm chỉ có một ký tự. */
    private val alphabet = listOf(
        "aàáảãạ", "ăằắẳẵặ", "âầấẩẫậ", "b", "c", "d", "đ", "eèéẻẽẹ", "êềếểễệ", "f", "g", "h", "iìíỉĩị", "j", "k",
        "l", "m", "n", "oòóỏõọ", "ôồốổỗộ", "ơờớởỡợ", "p", "q", "r", "s", "t", "uùúủũụ", "ưừứửữự", "v", "w", "x",
        "yỳýỷỹỵ", "z",
    )

    /** ký tự → (thứ tự chữ, thanh). */
    private val ranks: Map<Char, Pair<Int, Int>> = buildMap {
        alphabet.forEachIndexed { letter, group -> group.forEachIndexed { tone, char -> put(char, letter to tone) } }
    }

    override fun compare(a: String, b: String): Int {
        val left = a.lowercase()
        val right = b.lowercase()
        val byLetters = compareSequences(left, right) { primary(it) }
        if (byLetters != 0) return byLetters
        val byTones = compareSequences(left, right) { ranks[it]?.second ?: 0 }
        if (byTones != 0) return byTones
        return a.compareTo(b)
    }

    private fun primary(char: Char): Int = ranks[char]?.let { LETTER_BASE + it.first }
        ?: if (char.code < LETTER_BASE) char.code else OTHER_BASE + char.code

    private inline fun compareSequences(a: String, b: String, key: (Char) -> Int): Int {
        for (i in 0 until minOf(a.length, b.length)) {
            val diff = key(a[i]).compareTo(key(b[i]))
            if (diff != 0) return diff
        }
        return a.length.compareTo(b.length)
    }
}
