@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.capture.domain.repository

import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.model.CaptureEvent
import com.uit.finance.core.capture.domain.model.CaptureSettings
import com.uit.finance.core.capture.domain.model.IngestOutcome
import com.uit.finance.core.capture.domain.model.ParsedBankEvent
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.Transaction
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface CaptureSettingsRepository {
    /** Cài đặt của user đang đăng nhập; chưa đăng nhập thì [CaptureSettings.DEFAULT]. */
    fun observe(): Flow<CaptureSettings>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setBanks(banks: Set<BankId>)
    suspend fun setDefaultExpenseCategory(categoryId: String?)
    suspend fun setDefaultIncomeCategory(categoryId: String?)

    /** Ngân hàng chọn được ở bản đang chạy (bản release không có [BankId.DEMO]). */
    fun availableBanks(): List<BankId>
}

/** Một giao dịch nháp ở màn Chờ xác nhận. */
data class DraftItem(
    val transaction: Transaction,
    /** Ngân hàng, khi nháp do capture trên máy này tạo; nháp sync từ máy khác hay từ khoản định kỳ thì `null`. */
    val bank: BankId?,
    /** Bộ đọc chung, chưa chắc chắn: UI gắn nhãn "Kiểm tra kỹ". */
    val needsCheck: Boolean,
    /** Có giao dịch khác cùng ví, cùng loại, cùng số tiền, lệch ≤ 1 ngày (D8). */
    val possibleDuplicate: Boolean,
)

data class CaptureInbox(
    val drafts: List<DraftItem>,
    val needsAccount: List<CaptureEvent>,
    val unparsed: List<CaptureEvent>,
) {
    val isEmpty: Boolean get() = drafts.isEmpty() && needsAccount.isEmpty() && unparsed.isEmpty()

    companion object {
        val EMPTY = CaptureInbox(emptyList(), emptyList(), emptyList())
    }
}

/** Tin dán đã đọc, để điền sẵn form ở màn "Dán tin nhắn". */
data class PastePreview(
    val event: ParsedBankEvent,
    val occurredOn: LocalDate,
    val suggestedAccountId: String?,
    val suggestedCategoryId: String?,
) {
    override fun toString(): String = "PastePreview(event=$event)"
}

/** Lựa chọn của user ở màn dán trước khi bấm Lưu. */
data class PasteSubmission(
    val preview: PastePreview,
    val bank: BankId?,
    val accountId: String,
    val categoryId: String,
    val amountMinor: Long,
    val occurredOn: LocalDate,
    val note: String?,
    /** Lưu liên kết (ngân hàng, 4 số cuối) → ví cho lần sau. */
    val rememberAccount: Boolean,
) {
    override fun toString(): String = "PasteSubmission(bank=$bank, rememberAccount=$rememberAccount)"
}

sealed interface PasteParseResult {
    data class Ready(val preview: PastePreview) : PasteParseResult
    data object Otp : PasteParseResult
    data object NotReadable : PasteParseResult
}

interface CaptureRepository {
    /** Mọi nháp (kể cả sync từ máy khác) + tin cần chọn ví + tin không đọc được. Tự cập nhật. */
    fun observeInbox(): Flow<CaptureInbox>

    /** Số mục còn chờ user (để hiện badge). */
    fun observePendingCount(): Flow<Int>

    suspend fun confirm(transactionId: String): AppResult<Unit>

    /** Xoá nháp (tombstone lên server: "Bỏ qua" thắng giữa các máy, D7). */
    suspend fun dismissDraft(transactionId: String): AppResult<Unit>

    /** Bỏ một dòng "Cần chọn ví" / "Không đọc được". */
    suspend fun dismissEvent(eventId: String): AppResult<Unit>

    /** Gắn (ngân hàng, 4 số cuối) với ví rồi tạo nháp cho mọi tin đang chờ ví đó. Trả số nháp vừa tạo. */
    suspend fun linkAccount(bank: BankId, accountHint: String?, accountId: String): AppResult<Int>

    suspend fun parsePasted(text: String, bank: BankId?): PasteParseResult

    /** Lưu tin dán thành giao dịch **đã xác nhận** (B18). */
    suspend fun savePasted(submission: PasteSubmission): AppResult<IngestOutcome>

    /** Dọn nguyên văn quá hạn và dòng đã xong (S2, S3). Không đụng thông báo đang hiện. */
    suspend fun purgeExpired()

    /** User mở màn Chờ xác nhận: dọn hạn và gỡ thông báo "Có N giao dịch cần xác nhận" (B13). */
    suspend fun openInbox()
}
