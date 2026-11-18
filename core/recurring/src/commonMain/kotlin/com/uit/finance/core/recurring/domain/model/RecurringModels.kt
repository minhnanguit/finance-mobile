package com.uit.finance.core.recurring.domain.model

import com.uit.finance.core.common.id.uuidV5
import kotlinx.datetime.LocalDate

/** Chiều của khoản (ADR-009 R1). */
enum class RecurringType { EXPENSE, INCOME }

/** Chu kỳ lặp (ADR-009 R3). */
enum class Frequency { WEEKLY, MONTHLY, YEARLY }

/**
 * Lịch lặp của một phiên bản (R3). Field không dùng cho [frequency] là `null`.
 *
 * @param weekday ISO 1 = thứ Hai … 7 = Chủ Nhật (chỉ `WEEKLY`)
 * @param month 1–12 (chỉ `YEARLY`)
 * @param dayOfMonth 1–31 (`MONTHLY`, `YEARLY`); tháng thiếu ngày thì lùi về ngày cuối tháng (R5)
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekday: Int? = null,
    val month: Int? = null,
    val dayOfMonth: Int? = null,
) {
    /** Cùng nhịp: cùng chu kỳ, mỗi N, ngày/thứ/tháng. Đổi số tiền thì giữ nhịp; đổi nhịp thì đặt anchor mới (R4). */
    fun sameCadenceAs(other: RecurrenceRule): Boolean = this == other

    companion object {
        fun weekly(weekday: Int, interval: Int = 1) = RecurrenceRule(Frequency.WEEKLY, interval, weekday = weekday)
        fun monthly(dayOfMonth: Int, interval: Int = 1) = RecurrenceRule(Frequency.MONTHLY, interval, dayOfMonth = dayOfMonth)
        fun yearly(month: Int, dayOfMonth: Int, interval: Int = 1) =
            RecurrenceRule(Frequency.YEARLY, interval, month = month, dayOfMonth = dayOfMonth)
    }
}

/** Một phiên bản: áp từ [effectiveFrom] tới trước phiên bản kế tiếp; nhịp tính từ [anchorDate] (R2, R4). */
data class RecurrenceRevision(
    val id: String,
    val effectiveFrom: LocalDate,
    val anchorDate: LocalDate,
    val accountId: String,
    val categoryId: String,
    val amountMinor: Long,
    val note: String?,
    val rule: RecurrenceRule,
    val autoConfirm: Boolean,
) {
    /** Không in số tiền, ghi chú (ADR-006 B8). */
    override fun toString(): String = "RecurrenceRevision(id=$id, effectiveFrom=$effectiveFrom, rule=$rule)"
}

/**
 * Khoản định kỳ trên máy.
 *
 * @param revision revision của trạng thái local (RS9): lần ghi kế tiếp gửi số này làm điều kiện
 * @param blockedFrom ngày đầu không sinh được kỳ vì ví/danh mục bị chặn (R13); khác null thì chờ user quyết
 * @param hasPendingSync còn op chưa lên server: lịch chưa được chấp nhận, chỉ sinh nháp (R9)
 */
data class Recurring(
    val id: String,
    val name: String,
    val type: RecurringType,
    val currency: String,
    val generationNotBefore: LocalDate,
    val generationEndDate: LocalDate?,
    val remindDaysBefore: Int?,
    val revision: Long,
    val revisions: List<RecurrenceRevision>,
    val blockedFrom: LocalDate? = null,
    val hasPendingSync: Boolean = false,
) {
    /** Phiên bản áp cho [date], hoặc `null` khi [date] trước phiên bản đầu. */
    fun revisionOn(date: LocalDate): RecurrenceRevision? = revisions.lastOrNull { it.effectiveFrom <= date }

    /** Phiên bản đang áp ở [today], hoặc phiên bản đầu nếu khoản chưa bắt đầu. */
    fun currentRevision(today: LocalDate): RecurrenceRevision = revisionOn(today) ?: revisions.first()

    fun isEnded(today: LocalDate): Boolean = generationEndDate != null && generationEndDate < today

    /** Không in tên khoản (ADR-006 B8). */
    override fun toString(): String = "Recurring(id=$id, type=$type, currency=$currency, revision=$revision)"
}

/** Một kỳ đến hạn theo lịch: ngày và phiên bản áp cho ngày đó. */
data class ScheduledOccurrence(val recurringId: String, val date: LocalDate, val revision: RecurrenceRevision) {
    val transactionId: String get() = OccurrenceIds.of(recurringId, date)
}

/** Kỳ do chính máy này sinh, theo phiên bản nào (chỉ trên máy, R16). */
data class LocalOccurrence(
    val transactionId: String,
    val recurringId: String,
    val scheduledDate: LocalDate,
    val revisionId: String,
)

/** Giao dịch kỳ sinh ra là nháp hay ghi luôn (R5, R9). */
enum class OccurrenceStatus { DRAFT, CONFIRMED }

/** Kết quả ghi một kỳ (RS13). */
enum class OccurrenceResult {
    WRITTEN,

    /** Đã có, kể cả đã bỏ qua. */
    ALREADY_EXISTS,

    /** Ví/danh mục bị chặn (R13). */
    BLOCKED,

    /** Khoản vừa đổi revision giữa lúc tính: bỏ, tính lại lần sau. */
    STALE,
}

/** Id giao dịch của một kỳ (ADR-009 §2). */
object OccurrenceIds {
    const val NAMESPACE = "9ec1e9e3-0bb0-485a-ba55-77182d53d5f4"

    fun of(recurringId: String, date: LocalDate): String = uuidV5(NAMESPACE, "${recurringId.lowercase()}/$date")
}

/** Dữ liệu để tạo khoản mới (id do client sinh khi lưu). */
data class RecurringDraft(
    val name: String,
    val type: RecurringType,
    val accountId: String,
    val categoryId: String,
    val currency: String,
    val amountMinor: Long,
    val note: String?,
    val rule: RecurrenceRule,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val remindDaysBefore: Int?,
    val autoConfirm: Boolean,
    /** Bắt đầu trong quá khứ: `true` = ghi các kỳ trong 90 ngày qua thành nháp; `false` = chỉ từ hôm nay (R8). */
    val backfill: Boolean,
) {
    override fun toString(): String = "RecurringDraft(type=$type, currency=$currency, rule=$rule, startDate=$startDate)"
}

/** Thay đổi áp **từ [effectiveFrom]** (R10): tạo phiên bản mới, phiên bản tương lai sau ngày đó bị thay. */
data class RevisionChange(
    val effectiveFrom: LocalDate,
    val accountId: String,
    val categoryId: String,
    val amountMinor: Long,
    val note: String?,
    val rule: RecurrenceRule,
    val autoConfirm: Boolean,
) {
    override fun toString(): String = "RevisionChange(effectiveFrom=$effectiveFrom, rule=$rule)"
}

/** Mã lỗi dùng chung với backend (ADR-009 §6), đặt trong `FieldError.message`. */
object RecurringErrorCode {
    const val INVALID_FIELD = "recurring.invalid_field"
    const val LIMIT_REACHED = "recurring.limit_reached"
    const val NOT_FOUND = "recurring.not_found"
    const val IMMUTABLE_FIELD = "recurring.immutable_field"

    // Chỉ server trả (qua sync).
    const val REVISION_CONFLICT = "recurring.revision_conflict"
    const val INVALID_CATEGORY = "recurring.invalid_category"
    const val INVALID_ACCOUNT = "recurring.invalid_account"
    const val CURRENCY_MISMATCH = "recurring.currency_mismatch"
    const val REFERENCE_PENDING = "recurring.reference_pending"
    const val DELETED = "recurring.deleted"
}
