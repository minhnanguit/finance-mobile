@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.ledger.domain.model

import com.uit.finance.core.common.money.Money
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

enum class AccountType { CASH, BANK, EWALLET, OTHER }

enum class CategoryKind { INCOME, EXPENSE }

/** Chiều của tiền: số tiền luôn dương, chiều do type quyết định (ADR-005 D1). */
enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/** `DRAFT` không vào số dư (ADR-005 D6). */
enum class TransactionStatus { CONFIRMED, DRAFT }

/** Ví. `openingBalance` có dấu và mang luôn tiền tệ của ví. */
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val openingBalance: Money,
    val sortOrder: Int,
    val archived: Boolean,
) {
    val currency: String get() = openingBalance.currency
}

data class Category(
    val id: String,
    val kind: CategoryKind,
    val name: String,
    val parentId: String?,
    val icon: String?,
    val color: String?,
    /** Chỉ danh mục mặc định có, ví dụ `fee` (Phí giao dịch). Server điền. */
    val templateKey: String?,
    val archived: Boolean,
)

/**
 * Một dòng thu, chi hoặc chuyển tiền (ADR-005).
 *
 * @param occurredOn ngày theo lịch của user (D5)
 * @param occurredAt thời điểm, `null` khi không rõ giờ
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val status: TransactionStatus,
    val accountId: String,
    val counterAccountId: String?,
    val amount: Money,
    val categoryId: String?,
    val occurredOn: LocalDate,
    val occurredAt: Instant?,
    val payee: String?,
    val note: String?,
) {
    /** Không in số tiền, người nhận, ghi chú (ADR-006 B8). */
    override fun toString(): String = "Transaction(id=$id, type=$type, status=$status, accountId=$accountId)"
}

data class AccountBalance(val accountId: String, val balance: Money, val archived: Boolean)

/** Số dư từng ví và tổng theo từng tiền tệ, bỏ ví đã archive (ADR-005 §5). */
data class BalanceSummary(val accounts: List<AccountBalance>, val totals: List<Money>) {
    companion object {
        val EMPTY = BalanceSummary(emptyList(), emptyList())
    }
}

// ---- Dữ liệu để tạo mới (id do client sinh khi lưu) ----

data class AccountDraft(
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalanceMinor: Long,
    val sortOrder: Int = 0,
)

data class CategoryDraft(
    val kind: CategoryKind,
    val name: String,
    val parentId: String? = null,
    val icon: String? = null,
    val color: String? = null,
)

/** Tiền tệ không nằm ở đây: luôn lấy theo ví (ADR-005 §3). */
data class TransactionDraft(
    val type: TransactionType,
    val accountId: String,
    val amountMinor: Long,
    val occurredOn: LocalDate,
    val categoryId: String? = null,
    val counterAccountId: String? = null,
    val status: TransactionStatus = TransactionStatus.CONFIRMED,
    val occurredAt: Instant? = null,
    val payee: String? = null,
    val note: String? = null,
) {
    override fun toString(): String = "TransactionDraft(type=$type, status=$status, accountId=$accountId)"
}
