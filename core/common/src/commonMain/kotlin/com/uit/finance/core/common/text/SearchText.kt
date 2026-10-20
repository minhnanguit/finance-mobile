package com.uit.finance.core.common.text

/**
 * Dạng để so khớp khi tìm kiếm (docs/SEARCH-PLAN.md F5): viết thường, bỏ dấu, `đ` → `d`, gộp khoảng trắng.
 * "Cà phê  Highlands" → "ca phe highlands".
 *
 * Chạy đúng với cả chữ dựng sẵn (NFC) lẫn chữ tổ hợp (NFD, bàn phím iOS hay dữ liệu dán vào): chữ dựng sẵn đổi về
 * chữ gốc, mọi dấu tổ hợp U+0300–U+036F bị bỏ. Ký tự khác (số, ký hiệu, emoji) giữ nguyên.
 *
 * Từ khoá người dùng gõ, tên danh mục và cột `search_text` phải đi qua **cùng** hàm này, không thì không khớp.
 */
fun String.foldForSearch(): String {
    val lower = lowercase()
    val folded = StringBuilder(lower.length)
    var pendingSpace = false
    for (char in lower) {
        when {
            char in COMBINING_MARKS -> Unit
            char.isWhitespace() -> pendingSpace = folded.isNotEmpty()
            else -> {
                if (pendingSpace) folded.append(' ')
                pendingSpace = false
                folded.append(BASE_LETTERS[char] ?: char)
            }
        }
    }
    return folded.toString()
}

/** Ghép các phần (bỏ phần `null`) rồi [foldForSearch]. Giá trị cột `search_text` = `searchTextOf(payee, note)` (F4). */
fun searchTextOf(vararg parts: String?): String = parts.filterNotNull().joinToString(" ").foldForSearch()

private val COMBINING_MARKS = '\u0300'..'\u036F'

/** Chữ thường có dấu → chữ gốc: đủ bộ tiếng Việt, cộng các chữ Latin có dấu hay gặp. */
private val BASE_LETTERS: Map<Char, Char> = mapOf(
    'a' to "àáảãạăằắẳẵặâầấẩẫậäåāąǎ",
    'e' to "èéẻẽẹêềếểễệëēęěė",
    'i' to "ìíỉĩịîïīįı",
    'o' to "òóỏõọôồốổỗộơờớởỡợöøōő",
    'u' to "ùúủũụưừứửữựûüūůűų",
    'y' to "ỳýỷỹỵÿ",
    'd' to "đď",
    'c' to "çćč",
    'n' to "ñńň",
    's' to "śšş",
    'z' to "źżž",
    'g' to "ğ",
    'l' to "ł",
    'r' to "ř",
    't' to "ťţ",
).flatMap { (base, variants) -> variants.map { it to base } }.toMap()
