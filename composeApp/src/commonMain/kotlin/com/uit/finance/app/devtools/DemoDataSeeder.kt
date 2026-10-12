@file:OptIn(ExperimentalTime::class)

package com.uit.finance.app.devtools

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.asFailure
import com.uit.finance.core.common.result.asSuccess
import com.uit.finance.core.common.result.onFailure
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

internal data class LedgerCounts(val accounts: Int, val transactions: Int)

/**
 * Tạo/xoá dữ liệu mẫu cho sổ của user đang đăng nhập. Chỉ đăng ký ở build debug (`appModule`).
 *
 * Ghi qua repository của `core/ledger` giống hệt người dùng thật: validate, lưu DB trên máy, xếp outbox rồi
 * sync lên server. Nhờ vậy dữ liệu mẫu không lệch giữa máy và server, và còn nguyên sau khi cài lại app.
 * Không chèn thẳng SQL (bỏ qua outbox) hay seed Postgres (bỏ qua `change_seq`, sync pull không kéo về).
 */
internal class DemoDataSeeder(
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val clock: Clock,
) {
    private val timeZone = TimeZone.currentSystemDefault()

    /** Sổ đã có ví thì từ chối, để bấm hai lần không nhân đôi dữ liệu. */
    suspend fun seed(): AppResult<LedgerCounts> {
        if (accounts.observeAccounts().first().isNotEmpty()) {
            return AppError.Validation("accounts", "Sổ đã có ví. Xoá dữ liệu trước khi tạo mẫu để không bị trùng.").asFailure()
        }
        // Danh mục mặc định do server tạo ở lần sync đầu tiên (ADR-005), máy không tự sinh.
        val categoryIds = resolveCategories(categories.observeCategories().first())
            ?: return AppError.Validation("categories", "Chưa có danh mục mặc định. Đợi đồng bộ lần đầu xong rồi thử lại.").asFailure()

        val accountIds = mutableMapOf<DemoAccount, String>()
        DemoAccount.entries.forEachIndexed { index, demo ->
            val draft = AccountDraft(demo.title, demo.type, demo.currency, demo.openingBalanceMinor, sortOrder = index)
            when (val result = accounts.create(draft)) {
                is AppResult.Success -> accountIds[demo] = result.value.id
                is AppResult.Failure -> return result
            }
        }
        val today = clock.today(timeZone)
        DemoTransactions.forEachIndexed { index, demo ->
            transactions.record(demo.toDraft(index, today, accountIds, categoryIds)).onFailure { return it.asFailure() }
        }
        return LedgerCounts(accountIds.size, DemoTransactions.size).asSuccess()
    }

    /** Xoá mọi giao dịch rồi mọi ví (ví còn giao dịch thì không xoá được, D7). Danh mục giữ nguyên. */
    suspend fun clear(): AppResult<LedgerCounts> {
        val allTransactions = transactions.observeRecent(Int.MAX_VALUE).first()
        allTransactions.forEach { transactions.delete(it.id).onFailure { error -> return error.asFailure() } }
        val allAccounts = accounts.observeAccounts().first()
        allAccounts.forEach { accounts.delete(it.id).onFailure { error -> return error.asFailure() } }
        return LedgerCounts(allAccounts.size, allTransactions.size).asSuccess()
    }

    /** `null` khi thiếu hẳn một loại (thu hoặc chi). */
    private fun resolveCategories(all: List<Category>): Map<DemoCategory, String>? {
        val active = all.filterNot { it.archived }
        return DemoCategory.entries.associateWith { demo ->
            val sameKind = active.filter { it.kind == demo.kind }
            (sameKind.firstOrNull { it.icon == demo.iconKey } ?: sameKind.firstOrNull())?.id ?: return null
        }
    }

    private fun DemoTransaction.toDraft(
        index: Int,
        today: LocalDate,
        accountIds: Map<DemoAccount, String>,
        categoryIds: Map<DemoCategory, String>,
    ): TransactionDraft {
        val date = today.minus(daysAgo, DateTimeUnit.DAY)
        // Phút lệch theo thứ tự để các dòng cùng ngày không trùng giờ; không để giờ nằm ở tương lai.
        val occurredAt = hour
            ?.let { LocalDateTime(date, LocalTime(it, (index * 7) % 60)).toInstant(timeZone) }
            ?.let { minOf(it, clock.now()) }
        return TransactionDraft(
            type = type,
            accountId = accountIds.getValue(account),
            amountMinor = amountMinor,
            occurredOn = date,
            categoryId = category?.let(categoryIds::getValue),
            counterAccountId = counterAccount?.let(accountIds::getValue),
            status = if (draft) TransactionStatus.DRAFT else TransactionStatus.CONFIRMED,
            occurredAt = occurredAt,
            payee = payee,
            note = note,
        )
    }
}
