package com.uit.finance.core.capture.domain.parser

import com.uit.finance.core.capture.domain.model.BankMessage
import com.uit.finance.core.capture.domain.model.Confidence
import com.uit.finance.core.capture.domain.model.Direction
import com.uit.finance.core.capture.domain.model.IgnoreReason
import com.uit.finance.core.capture.domain.model.ParseResult
import com.uit.finance.core.capture.domain.model.ParsedBankEvent
import com.uit.finance.core.common.text.foldForSearch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * Đọc thông báo biến động số dư (plan mục 2.1). Kotlin thuần, dùng chung Android và iOS.
 *
 * Thứ tự cố định: **lọc OTP trước** (P5) → tìm số tiền có chiều (P4) → số dư, 4 số cuối, giờ, nội dung → che số (P7).
 * Chưa có mẫu riêng từng ngân hàng (cần mẫu thật ở Phase 0), nên mọi tin đi qua bộ đọc chung: tin có đủ số dư sau
 * và 4 số cuối tài khoản được coi là `HIGH` (cấu trúc biến động chuẩn), còn lại `LOW` → UI gắn nhãn "Kiểm tra kỹ".
 */
object BankMessageParser {

    fun parse(message: BankMessage): ParseResult {
        val original = listOfNotNull(message.title, message.text).joinToString(" \n ")
        val folded = original.foldForSearch().uppercase()

        if (OtpFilter.isOtp(folded)) return ParseResult.Ignored(IgnoreReason.OTP)

        val balance = BALANCE.find(folded)
        // Khuyến mãi/ưu đãi hay có số tiền ("nhận ưu đãi 50.000đ"): bỏ trước khi tìm số tiền (P6). Chỉ xét phần trước
        // nội dung chuyển khoản, và chỉ khi tin không có số dư: biến động thật có số dư, nội dung có thể chứa "ưu đãi".
        val header = DESCRIPTION.find(folded)?.let { folded.substring(0, it.range.first) } ?: folded
        if (balance == null && IGNORE_WORDS.any { it in header }) return ParseResult.Ignored(IgnoreReason.NOT_ACCOUNT_MOVEMENT)
        val movement = findMovement(folded, balance?.range)
        if (movement == null) {
            return if (MOVEMENT_MARKERS.containsMatchIn(folded)) ParseResult.Unparsed else ParseResult.Ignored(IgnoreReason.NOT_ACCOUNT_MOVEMENT)
        }
        if (CARD_WORDS.any { it in folded }) return ParseResult.Ignored(IgnoreReason.NOT_ACCOUNT_MOVEMENT)
        val amount = when (val parsed = AmountParser.parse(movement.digits, movement.currency)) {
            is AmountParser.Result.Amount -> parsed.minor
            AmountParser.Result.NotVnd -> return ParseResult.Ignored(IgnoreReason.NOT_VND)
            AmountParser.Result.Invalid -> return ParseResult.Unparsed
        }
        if (amount <= 0) return ParseResult.Unparsed

        val balanceAfter = balance?.let { (AmountParser.parse(it.groupValues[1], it.groupValues[2]) as? AmountParser.Result.Amount)?.minor }
        val accountHint = ACCOUNT.find(folded)?.groupValues?.get(1)?.let(::lastFourDigits)
        val description = DESCRIPTION.find(original)?.groupValues?.get(1)?.trim()
            ?.let(Masking::mask)
            ?.take(ParsedBankEvent.MAX_DESCRIPTION)
            ?.ifBlank { null }

        return ParseResult.Parsed(
            ParsedBankEvent(
                bank = message.bank,
                direction = movement.direction,
                amountMinor = amount,
                balanceAfterMinor = balanceAfter,
                accountHint = accountHint,
                description = description,
                payee = null,
                postedAt = postedAt(folded),
                confidence = if (balanceAfter != null && accountHint != null) Confidence.HIGH else Confidence.LOW,
            ),
        )
    }

    /**
     * Ghi chú điền sẵn khi user tự nhập từ một tin không đọc được: đã che số tài khoản (P7), tối đa 500 ký tự.
     * Không bao giờ dùng nguyên văn tin cho dữ liệu được sync (B10).
     */
    fun manualEntryNote(text: String): String? =
        Masking.mask(text.trim()).take(ParsedBankEvent.MAX_DESCRIPTION).ifBlank { null }

    private class Movement(val direction: Direction, val digits: String, val currency: String)

    /** Số tiền có dấu `+/-` (không nằm trong cụm số dư), hoặc đứng sau từ khoá "ghi có/ghi nợ/nhận/chuyển". */
    private fun findMovement(folded: String, balanceRange: IntRange?): Movement? {
        SIGNED_AMOUNT.findAll(folded)
            .firstOrNull { match -> balanceRange == null || match.range.first !in balanceRange }
            ?.let { match ->
                val direction = if (match.groupValues[1] == "+") Direction.IN else Direction.OUT
                return Movement(direction, match.groupValues[2], match.groupValues[3])
            }
        // Theo từ khoá thì dễ bắt nhầm số tài khoản/mã giao dịch: chỉ nhận số có đơn vị tiền hoặc dấu phân cách hàng
        // nghìn, và không đứng ngay sau "TK"/"tài khoản".
        KEYWORD_AMOUNT.findAll(folded)
            .firstOrNull { match ->
                val gap = match.groupValues[2]
                val digits = match.groupValues[3]
                val looksLikeMoney = match.groupValues[4].isNotEmpty() || digits.any { it == '.' || it == ',' }
                looksLikeMoney && !ACCOUNT_BEFORE_NUMBER.containsMatchIn(gap)
            }
            ?.let { match ->
                val keyword = match.groupValues[1]
                val direction = if (IN_KEYWORDS.any { keyword.startsWith(it) }) Direction.IN else Direction.OUT
                return Movement(direction, match.groupValues[3], match.groupValues[4])
            }
        return null
    }

    private fun lastFourDigits(raw: String): String? {
        val digits = raw.takeLastWhile { it.isDigit() }
        return if (digits.length >= 4) digits.takeLast(4) else null
    }

    private fun postedAt(folded: String): LocalDateTime? {
        val date = DATE.find(folded)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = match.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            runCatching { LocalDate(year, month, day) }.getOrNull()
        } ?: return null
        val time = TIME.find(folded)?.let { match ->
            runCatching { LocalTime(match.groupValues[1].toInt(), match.groupValues[2].toInt()) }.getOrNull()
        } ?: return null
        return LocalDateTime(date, time)
    }

    // Số tiền: 1,234,567 · 1.234.567 · 1234567, có thể kèm phần lẻ (để AmountParser từ chối), hậu tố tiền tệ.
    private const val NUMBER = """(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)"""
    private const val CURRENCY = """\s?(VND|VNĐ|USD|EUR|D\b)?"""

    private val SIGNED_AMOUNT = Regex("""(?<![\w])([+-])\s?$NUMBER$CURRENCY""")
    private val IN_KEYWORDS = listOf("GHI CO", "NHAN", "CONG")
    private val KEYWORD_AMOUNT = Regex("""(GHI CO|GHI NO|NHAN|CONG|TRU|CHUYEN|THANH TOAN)([^0-9]{0,20})$NUMBER$CURRENCY""")
    private val ACCOUNT_BEFORE_NUMBER = Regex("""(\bTK|TAI KHOAN|\bSTK|SO TK)\W*$""")

    // "SD: 1,234,567VND", "So du 1.234.567 VND". Không bắt "So du TK 001..." (số tài khoản).
    private val BALANCE = Regex("""(?:\bSD|\bSO DU)(?:\s*(?:KHA DUNG|CUOI))?\s*[:.]?\s*$NUMBER$CURRENCY""")
    private val ACCOUNT = Regex("""(?:\bTK|\bTAI KHOAN)\s*(?:SO\s*)?[:.]?\s*([0-9X*.]{4,})""")
    private val DATE = Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{4}|\d{2})\b""")
    private val TIME = Regex("""\b([01]?\d|2[0-3])[:H](\d{2})""")
    private val DESCRIPTION = Regex("""(?i)(?:\bND|\bNội dung|\bNoi dung|\bND GD)\s*[:]\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)

    private val MOVEMENT_MARKERS = Regex("""\b(SD|SO DU|GD|GIAO DICH|BIEN DONG|TK)\b""")
    private val IGNORE_WORDS = listOf("KHUYEN MAI", "UU DAI", "QUA TANG", "HOAN TIEN DEN")
    private val CARD_WORDS = listOf("THE TIN DUNG", "CREDIT CARD")
}

/** Lọc OTP chạy **đầu tiên**, trên chuỗi đã bỏ dấu + viết hoa (P5). Tin OTP không bao giờ được lưu. */
internal object OtpFilter {
    private val OTP = Regex("""\bOTP\b|MA XAC THUC|MA XAC NHAN|MAT KHAU|SMART ?OTP""")
    fun isOtp(folded: String): Boolean = OTP.containsMatchIn(folded)
}

/** Che mọi dãy ≥ 6 chữ số thành `•••` + 4 số cuối (P7), áp trước khi kết quả rời parser. */
internal object Masking {
    private val LONG_NUMBER = Regex("""\d{6,}""")
    fun mask(text: String): String = LONG_NUMBER.replace(text) { "•••" + it.value.takeLast(4) }
}

/** Số tiền VND: hiểu `1,234,567` và `1.234.567`; có phần lẻ hoặc tiền tệ khác → không phải VND (P3). */
object AmountParser {
    sealed interface Result {
        data class Amount(val minor: Long) : Result
        data object NotVnd : Result
        data object Invalid : Result
    }

    private val GROUPED = Regex("""\d{1,3}(?:([.,])\d{3})+""")

    fun parse(number: String, currency: String?): Result {
        val unit = currency?.trim()?.uppercase().orEmpty()
        if (unit == "USD" || unit == "EUR") return Result.NotVnd
        val digits = when {
            number.all { it.isDigit() } -> number
            GROUPED.matches(number) -> {
                // Chỉ một loại dấu phân cách hàng nghìn; trộn hai loại là có phần lẻ.
                val separators = number.filter { it == '.' || it == ',' }.toSet()
                if (separators.size != 1) return Result.NotVnd
                number.filter { it.isDigit() }
            }
            else -> return Result.NotVnd // "12.5", "1,234.50": có phần lẻ
        }
        if (digits.length > MAX_DIGITS) return Result.Invalid
        return digits.toLongOrNull()?.let { Result.Amount(it) } ?: Result.Invalid
    }

    /** 10¹⁵ là giới hạn số tiền của ledger. */
    private const val MAX_DIGITS = 15
}
