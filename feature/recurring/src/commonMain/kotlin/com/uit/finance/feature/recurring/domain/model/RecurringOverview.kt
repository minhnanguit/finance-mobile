package com.uit.finance.feature.recurring.domain.model

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringType
import kotlinx.datetime.LocalDate

/** Toàn cảnh màn "Khoản định kỳ" (plan mục 2.1). */
data class RecurringOverview(
    val today: LocalDate,
    /** Khoản bị chặn vì ví/danh mục (R13). */
    val attention: List<AttentionItem>,
    /** Giao dịch nháp của kỳ đúng lịch hiện hành, mới nhất trước. */
    val toConfirm: List<OccurrenceItem>,
    /** Nháp do máy này sinh theo lịch đã bị thay (R16). */
    val toReview: List<OccurrenceItem>,
    /** Kỳ từ ngày mai tới hôm nay + 29, tăng dần. */
    val upcoming: List<UpcomingItem>,
    val upcomingTotals: List<UpcomingTotal>,
    val recurrings: List<RecurringSummary>,
) {
    companion object {
        fun empty(today: LocalDate) = RecurringOverview(today, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

data class OccurrenceItem(
    val transactionId: String,
    val recurringId: String?,
    val name: String,
    val date: LocalDate,
    val amount: Money,
    val type: RecurringType,
) {
    override fun toString(): String = "OccurrenceItem(transactionId=$transactionId, date=$date)"
}

data class UpcomingItem(
    val recurringId: String,
    val name: String,
    val date: LocalDate,
    val amount: Money,
    val type: RecurringType,
) {
    override fun toString(): String = "UpcomingItem(recurringId=$recurringId, date=$date)"
}

/** Tổng sắp tới của một tiền tệ, không cộng chéo tiền tệ. */
data class UpcomingTotal(val currency: String, val expense: Money, val income: Money)

enum class AttentionReason { BLOCKED, ACCOUNT_UNAVAILABLE, CATEGORY_UNAVAILABLE }

data class AttentionItem(val recurring: Recurring, val reason: AttentionReason)

data class RecurringSummary(
    val recurring: Recurring,
    val next: LocalDate?,
    val isEnded: Boolean,
    val needsAttention: Boolean,
)

/** Dữ liệu cho form tạo/sửa: ví và danh mục đang dùng, khoản đang sửa (nếu có). */
data class RecurringFormData(
    val accounts: List<Account>,
    val categories: List<Category>,
    /** Không có khi tạo mới, hoặc khi khoản đang sửa vừa bị xoá (có thể ở máy khác). */
    val recurring: Recurring?,
    val today: LocalDate,
)
