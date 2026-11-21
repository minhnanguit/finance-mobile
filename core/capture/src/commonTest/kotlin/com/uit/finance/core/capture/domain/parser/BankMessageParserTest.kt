@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.capture.domain.parser

import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.BankMessage
import com.uit.finance.core.capture.domain.model.Confidence
import com.uit.finance.core.capture.domain.model.Direction
import com.uit.finance.core.capture.domain.model.IgnoreReason
import com.uit.finance.core.capture.domain.model.ParseResult
import com.uit.finance.core.capture.domain.model.ParsedBankEvent
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime

/**
 * Mẫu dưới đây là **mẫu minh hoạ** theo định dạng biến động hay gặp, chưa phải tin thật. Phase 0 thay bằng tin thật
 * đã che số tài khoản và tên người (P8).
 */
class BankMessageParserTest {

    private fun parse(text: String, title: String? = null, bank: BankId? = BankId.DEMO): ParseResult =
        BankMessageParser.parse(BankMessage(bank, title, text, Instant.parse("2026-11-16T02:15:00Z")))

    private fun parsed(text: String, title: String? = null): ParsedBankEvent =
        assertIs<ParseResult.Parsed>(parse(text, title)).event

    @Test
    fun `chi tieu kieu TK - so tien - SD - ND`() {
        val event = parsed("TK ...5678 -45,000VND luc 09:15 16/11/2026. SD: 1,189,567VND. ND: HIGHLANDS COFFEE")
        assertEquals(Direction.OUT, event.direction)
        assertEquals(45_000, event.amountMinor)
        assertEquals(1_189_567, event.balanceAfterMinor)
        assertEquals("5678", event.accountHint)
        assertEquals("HIGHLANDS COFFEE", event.description)
        assertEquals(LocalDateTime(2026, 11, 16, 9, 15), event.postedAt)
        assertEquals(Confidence.HIGH, event.confidence)
    }

    @Test
    fun `tien vao dau cham phan cach va VND co dau`() {
        val event = parsed("Số dư TK 0123XXX5678 +1.500.000 VNĐ lúc 16-11-2026 20:01:33. Số dư 3.200.000 VNĐ. Nội dung: Luong thang 11")
        assertEquals(Direction.IN, event.direction)
        assertEquals(1_500_000, event.amountMinor)
        assertEquals(3_200_000, event.balanceAfterMinor)
        assertEquals("5678", event.accountHint)
        assertEquals("Luong thang 11", event.description)
    }

    @Test
    fun `dang ong dung cach va GD`() {
        val event = parsed("TK 07xxx4321|GD: -120,000VND 16/11/26 12:30 |SD: 880,000VND|ND: grab")
        assertEquals(Direction.OUT, event.direction)
        assertEquals(120_000, event.amountMinor)
        assertEquals(880_000, event.balanceAfterMinor)
        assertEquals("4321", event.accountHint)
        assertEquals(LocalDateTime(2026, 11, 16, 12, 30), event.postedAt)
    }

    @Test
    fun `tu khoa ghi co khong co dau cong tru`() {
        val event = parsed("Tai khoan 9999 ghi co 200,000 VND. ND chuyen tien")
        assertEquals(Direction.IN, event.direction)
        assertEquals(200_000, event.amountMinor)
        assertEquals(Confidence.LOW, event.confidence)
    }

    @Test
    fun `thieu so du thi do tin cay thap`() {
        val event = parsed("TK ...5678 -45,000VND. ND: an trua")
        assertNull(event.balanceAfterMinor)
        assertEquals(Confidence.LOW, event.confidence)
    }

    @Test
    fun `khong lay so du lam so tien`() {
        val event = parsed("SD: 2,000,000VND sau GD -30,000VND")
        assertEquals(30_000, event.amountMinor)
        assertEquals(2_000_000, event.balanceAfterMinor)
    }

    @Test
    fun `OTP co dau va khong dau deu bi bo`() {
        assertEquals(ParseResult.Ignored(IgnoreReason.OTP), parse("Ma OTP cua quy khach la 123456. -50,000VND"))
        assertEquals(ParseResult.Ignored(IgnoreReason.OTP), parse("Mã xác thực giao dịch 654321, số tiền -50.000đ"))
        assertEquals(ParseResult.Ignored(IgnoreReason.OTP), parse("Mật khẩu một lần: 112233"))
    }

    @Test
    fun `khuyen mai co so tien khong ra giao dich`() {
        assertIs<ParseResult.Ignored>(parse("Uu dai hoan tien den 50,000VND khi thanh toan QR"))
    }

    @Test
    fun `the tin dung bi bo`() {
        assertEquals(
            ParseResult.Ignored(IgnoreReason.NOT_ACCOUNT_MOVEMENT),
            parse("The tin dung ...1111 -500,000VND tai SHOPEE"),
        )
    }

    @Test
    fun `ngoai te va phan le bi bo`() {
        assertEquals(ParseResult.Ignored(IgnoreReason.NOT_VND), parse("TK ...5678 -12.50 USD. SD 100 USD"))
        assertEquals(ParseResult.Ignored(IgnoreReason.NOT_VND), parse("TK ...5678 -1,234.50"))
    }

    @Test
    fun `tin ngan hang khong tach duoc thi Unparsed`() {
        assertEquals(ParseResult.Unparsed, parse("Bien dong so du TK cua quy khach. Mo app de xem chi tiet"))
    }

    @Test
    fun `tin khong lien quan bi bo`() {
        assertEquals(ParseResult.Ignored(IgnoreReason.NOT_ACCOUNT_MOVEMENT), parse("Chao mung quy khach den voi ung dung"))
    }

    @Test
    fun `ngay trong tin khong bi hieu la so tien am`() {
        val event = parsed("TK ...5678 +10,000VND luc 16-11-2026 09:00. ND: hoan tien")
        assertEquals(Direction.IN, event.direction)
        assertEquals(10_000, event.amountMinor)
    }

    @Test
    fun `so tai khoan dai trong noi dung bi che`() {
        val event = parsed("TK ...5678 -500,000VND. SD: 1,000,000VND. ND: CK den 0123456789 NGUYEN VAN A")
        val description = event.description.orEmpty()
        assertFalse("0123456789" in description)
        assertTrue("•••6789" in description)
    }

    @Test
    fun `tieu de duoc doc cung noi dung`() {
        val event = parsed(text = "-45,000VND. SD: 1,000,000VND", title = "TK ...5678")
        assertEquals("5678", event.accountHint)
    }

    @Test
    fun `toString khong in so tien`() {
        val event = parsed("TK ...5678 -45,000VND. SD: 1,189,567VND. ND: HIGHLANDS")
        assertFalse("45000" in event.toString())
        assertFalse("HIGHLANDS" in event.toString())
    }

    @Test
    fun `AmountParser doc lai dung so voi hai kieu phan cach`() {
        val random = Random(42)
        repeat(500) {
            val value = random.nextLong(1, 1_000_000_000_000)
            val grouped = value.toString().reversed().chunked(3).joinToString(",").reversed()
            assertEquals(AmountParser.Result.Amount(value), AmountParser.parse(grouped, "VND"), grouped)
            assertEquals(AmountParser.Result.Amount(value), AmountParser.parse(grouped.replace(',', '.'), null), grouped)
        }
    }
}
