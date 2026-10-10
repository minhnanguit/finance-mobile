package com.uit.finance.feature.home.testing

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountBalance
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.BalanceSummary
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryDraft
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.BalanceRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/** 2023-11-14T12:00:00Z: giữa trưa UTC nên `today()` là 14/11 ở mọi múi giờ dev/CI (UTC, UTC+7). */
@OptIn(ExperimentalTime::class)
val NOON_2023_11_14: Instant = Instant.fromEpochSeconds(1_699_963_200L)

/*
 * Fake chỉ cho phần đọc (trang chủ chỉ observe). Phần ghi không dùng tới nên báo lỗi ngay nếu bị gọi,
 * thay vì âm thầm trả Success.
 */

private fun notUsed(): Nothing = error("trang chủ không ghi dữ liệu")

class FakeAccountRepository : AccountRepository {
    val accounts = MutableStateFlow<List<Account>>(emptyList())
    override fun observeAccounts(): Flow<List<Account>> = accounts
    override suspend fun create(draft: AccountDraft): AppResult<Account> = notUsed()
    override suspend fun update(account: Account): AppResult<Account> = notUsed()
    override suspend fun setArchived(accountId: String, archived: Boolean): AppResult<Unit> = notUsed()
    override suspend fun delete(accountId: String): AppResult<Unit> = notUsed()
}

class FakeCategoryRepository : CategoryRepository {
    val categories = MutableStateFlow<List<Category>>(emptyList())
    override fun observeCategories(): Flow<List<Category>> = categories
    override suspend fun create(draft: CategoryDraft): AppResult<Category> = notUsed()
    override suspend fun update(category: Category): AppResult<Category> = notUsed()
    override suspend fun setArchived(categoryId: String, archived: Boolean): AppResult<Unit> = notUsed()
    override suspend fun delete(categoryId: String): AppResult<Unit> = notUsed()
}

class FakeTransactionRepository : TransactionRepository {
    val recent = MutableStateFlow<List<Transaction>>(emptyList())
    var requestedLimit: Int? = null
    var observeRecentCalls = 0
    override fun observeRecent(limit: Int): Flow<List<Transaction>> {
        requestedLimit = limit
        observeRecentCalls++
        return recent
    }
    override fun observe(transactionId: String): Flow<Transaction?> = notUsed()
    override suspend fun record(draft: TransactionDraft): AppResult<Transaction> = notUsed()
    override suspend fun update(transaction: Transaction): AppResult<Transaction> = notUsed()
    override suspend fun delete(transactionId: String): AppResult<Unit> = notUsed()
}

class FakeBalanceRepository : BalanceRepository {
    val balances = MutableStateFlow(BalanceSummary.EMPTY)
    val cashFlow = MutableStateFlow(CashFlow.EMPTY)
    var requestedRange: Pair<LocalDate, LocalDate>? = null
    override fun observeBalances(): Flow<BalanceSummary> = balances
    override fun observeCashFlow(from: LocalDate, to: LocalDate): Flow<CashFlow> {
        requestedRange = from to to
        return cashFlow
    }
}

// ---- Dữ liệu mẫu ----

fun account(id: String, name: String = "Ví $id", currency: String = "VND", archived: Boolean = false, type: AccountType = AccountType.CASH) =
    Account(id = id, name = name, type = type, openingBalance = Money(0, currency), sortOrder = 0, archived = archived)

fun balance(accountId: String, amountMinor: Long, currency: String = "VND") =
    AccountBalance(accountId = accountId, balance = Money(amountMinor, currency), archived = false)

fun category(id: String, name: String, icon: String? = null, kind: CategoryKind = CategoryKind.EXPENSE) =
    Category(id = id, kind = kind, name = name, parentId = null, icon = icon, color = null, templateKey = null, archived = false)

fun transaction(
    id: String,
    type: TransactionType,
    accountId: String,
    amountMinor: Long,
    occurredOn: LocalDate,
    currency: String = "VND",
    categoryId: String? = null,
    payee: String? = null,
) = Transaction(
    id = id,
    type = type,
    status = TransactionStatus.CONFIRMED,
    accountId = accountId,
    counterAccountId = null,
    amount = Money(amountMinor, currency),
    categoryId = categoryId,
    occurredOn = occurredOn,
    occurredAt = null,
    payee = payee,
    note = null,
)
