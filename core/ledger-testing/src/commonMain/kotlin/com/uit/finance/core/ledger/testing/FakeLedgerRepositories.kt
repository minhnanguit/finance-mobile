@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.ledger.testing

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.text.foldForSearch
import com.uit.finance.core.common.text.searchTextOf
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
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionTotals
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.BalanceRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/*
 * Fake trong bộ nhớ cho repository của core/ledger. Không kiểm luật nghiệp vụ (đó là việc của
 * LedgerRepositoriesTest chạy trên SQLite thật); chỉ lưu, phát Flow và ghi lại lời gọi để test ViewModel /
 * use case. Đặt [failNext] để lần ghi kế tiếp trả lỗi.
 */

/** 2023-11-14T12:00:00Z: giữa trưa UTC nên `today()` là 14/11 ở mọi múi giờ dev/CI (UTC, UTC+7). */
val NOON_2023_11_14: Instant = Instant.fromEpochSeconds(1_699_963_200L)

abstract class FakeWriter {
    /** Lỗi trả cho lần ghi kế tiếp (rồi tự xoá). */
    var failNext: AppError? = null

    protected inline fun <T> write(block: () -> T): AppResult<T> {
        failNext?.let {
            failNext = null
            return AppResult.Failure(it)
        }
        return AppResult.Success(block())
    }
}

class FakeAccountRepository : FakeWriter(), AccountRepository {
    val accounts = MutableStateFlow<List<Account>>(emptyList())
    private var nextId = 0

    override fun observeAccounts(): Flow<List<Account>> = accounts.map { list -> list.sortedBy { it.sortOrder } }

    override suspend fun create(draft: AccountDraft): AppResult<Account> = write {
        Account(
            id = "account-${++nextId}",
            name = draft.name.trim(),
            type = draft.type,
            openingBalance = Money(draft.openingBalanceMinor, draft.currency),
            sortOrder = draft.sortOrder,
            archived = false,
        ).also { created -> accounts.update { it + created } }
    }

    override suspend fun update(account: Account): AppResult<Account> = write {
        accounts.update { list -> list.map { if (it.id == account.id) account else it } }
        account
    }

    override suspend fun setArchived(accountId: String, archived: Boolean): AppResult<Unit> = write {
        accounts.update { list -> list.map { if (it.id == accountId) it.copy(archived = archived) else it } }
    }

    override suspend fun reorder(orderedIds: List<String>): AppResult<Unit> = write {
        accounts.update { list -> list.map { account -> account.copy(sortOrder = orderedIds.indexOf(account.id)) } }
    }

    override suspend fun delete(accountId: String): AppResult<Unit> = write {
        accounts.update { list -> list.filterNot { it.id == accountId } }
    }
}

class FakeCategoryRepository : FakeWriter(), CategoryRepository {
    val categories = MutableStateFlow<List<Category>>(emptyList())
    private var nextId = 0

    override fun observeCategories(): Flow<List<Category>> = categories

    override suspend fun create(draft: CategoryDraft): AppResult<Category> = write {
        Category(
            id = "category-${++nextId}",
            kind = draft.kind,
            name = draft.name.trim(),
            parentId = draft.parentId,
            icon = draft.icon,
            color = draft.color,
            templateKey = null,
            archived = false,
        ).also { created -> categories.update { it + created } }
    }

    override suspend fun update(category: Category): AppResult<Category> = write {
        categories.update { list -> list.map { if (it.id == category.id) category else it } }
        category
    }

    override suspend fun setArchived(categoryId: String, archived: Boolean): AppResult<Unit> = write {
        categories.update { list -> list.map { if (it.id == categoryId) it.copy(archived = archived) else it } }
    }

    override suspend fun delete(categoryId: String): AppResult<Unit> = write {
        categories.update { list -> list.filterNot { it.id == categoryId } }
    }
}

/**
 * [observeFiltered] lọc thật trên list trong bộ nhớ, cùng luật với câu SQL (docs/SEARCH-PLAN.md): bỏ dấu, cha gồm
 * con, chuyển tiền khớp ví nhận. Truyền [categories] để tìm theo tên danh mục và lọc danh mục cha.
 */
class FakeTransactionRepository(
    private val accounts: FakeAccountRepository? = null,
    private val categories: FakeCategoryRepository? = null,
) : FakeWriter(), TransactionRepository {
    val transactions = MutableStateFlow<List<Transaction>>(emptyList())

    /** Limit của mỗi lần `observeRecent`, theo thứ tự gọi. */
    val requestedLimits = mutableListOf<Int>()

    /** Filter + limit của mỗi lần `observeFiltered`, theo thứ tự gọi. */
    val requestedFilters = mutableListOf<Pair<TransactionFilter, Int>>()
    val recordedBatches = mutableListOf<List<TransactionDraft>>()
    val deletedIds = mutableListOf<String>()
    private var nextId = 0

    val requestedLimit: Int? get() = requestedLimits.lastOrNull()

    override fun observeRecent(limit: Int): Flow<List<Transaction>> {
        requestedLimits += limit
        // Ngày giảm dần; cùng ngày thì giữ thứ tự thêm vào (sort ổn định) để test dễ đoán.
        return transactions.map { list -> list.sortedByDescending { it.occurredOn }.take(limit) }
    }

    override fun observe(transactionId: String): Flow<Transaction?> =
        transactions.map { list -> list.firstOrNull { it.id == transactionId } }

    override fun observeFiltered(filter: TransactionFilter, limit: Int): Flow<List<Transaction>> {
        requestedFilters += filter to limit
        return matching(filter).map { it.take(limit) }
    }

    override fun observeFilteredTotals(filter: TransactionFilter): Flow<TransactionTotals> = matching(filter).map { matched ->
        val confirmed = matched.filter { it.status == TransactionStatus.CONFIRMED }
        fun totalsOf(type: TransactionType) = confirmed.filter { it.type == type }
            .groupBy { it.amount.currency }.entries.sortedBy { it.key }
            .map { (currency, items) -> Money(items.sumOf { it.amount.amountMinor }, currency) }
        TransactionTotals(
            count = matched.size,
            draftCount = matched.count { it.status == TransactionStatus.DRAFT },
            cashFlow = CashFlow(income = totalsOf(TransactionType.INCOME), expense = totalsOf(TransactionType.EXPENSE)),
        )
    }

    private fun matching(filter: TransactionFilter): Flow<List<Transaction>> {
        val normalized = filter.normalized()
        return combine(transactions, categories?.categories ?: flowOf(emptyList())) { list, categoryList ->
            val match = FilterMatcher(normalized, categoryList)
            list.filter(match::matches).sortedByDescending { it.occurredOn }
        }
    }

    private class FilterMatcher(private val filter: TransactionFilter, private val categories: List<Category>) {
        private val query = filter.query?.foldForSearch().orEmpty()
        private val categoryIds = filter.categoryIds?.let(::withChildren)
        private val textCategoryIds = withChildren(categories.filter { query in it.name.foldForSearch() }.map { it.id }.toSet())

        private fun withChildren(ids: Set<String>): Set<String> = ids + categories.filter { it.parentId in ids }.map { it.id }

        fun matches(transaction: Transaction): Boolean = with(transaction) {
            val from = filter.from
            val to = filter.to
            (from == null || occurredOn >= from) &&
                (to == null || occurredOn <= to) &&
                filter.types.let { it == null || type in it } &&
                filter.accountIds.let { it == null || accountId in it || counterAccountId in it } &&
                categoryIds.let { it == null || categoryId in it } &&
                filter.status.let { it == null || status == it } &&
                (query.isEmpty() || query in searchTextOf(payee, note) || categoryId in textCategoryIds)
        }
    }

    override suspend fun record(draft: TransactionDraft): AppResult<Transaction> = when (val result = recordAll(listOf(draft))) {
        is AppResult.Success -> AppResult.Success(result.value.single())
        is AppResult.Failure -> result
    }

    override suspend fun recordAll(drafts: List<TransactionDraft>): AppResult<List<Transaction>> = write {
        recordedBatches += drafts
        drafts.map { draft ->
            val currency = accounts?.accounts?.value?.firstOrNull { it.id == draft.accountId }?.currency ?: "VND"
            Transaction(
                id = "transaction-${++nextId}",
                type = draft.type,
                status = draft.status,
                accountId = draft.accountId,
                counterAccountId = draft.counterAccountId,
                amount = Money(draft.amountMinor, currency),
                categoryId = draft.categoryId,
                occurredOn = draft.occurredOn,
                occurredAt = draft.occurredAt,
                payee = draft.payee,
                note = draft.note,
            )
        }.also { created -> transactions.update { it + created } }
    }

    override suspend fun update(transaction: Transaction): AppResult<Transaction> = write {
        transactions.update { list -> list.map { if (it.id == transaction.id) transaction else it } }
        transaction
    }

    override suspend fun delete(transactionId: String): AppResult<Unit> = write {
        deletedIds += transactionId
        transactions.update { list -> list.filterNot { it.id == transactionId } }
    }
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

fun account(
    id: String,
    name: String = "Ví $id",
    currency: String = "VND",
    archived: Boolean = false,
    type: AccountType = AccountType.CASH,
    sortOrder: Int = 0,
    openingMinor: Long = 0,
) = Account(id = id, name = name, type = type, openingBalance = Money(openingMinor, currency), sortOrder = sortOrder, archived = archived)

fun balance(accountId: String, amountMinor: Long, currency: String = "VND", archived: Boolean = false) =
    AccountBalance(accountId = accountId, balance = Money(amountMinor, currency), archived = archived)

fun category(
    id: String,
    name: String,
    icon: String? = null,
    kind: CategoryKind = CategoryKind.EXPENSE,
    templateKey: String? = null,
    archived: Boolean = false,
    color: String? = null,
) = Category(id = id, kind = kind, name = name, parentId = null, icon = icon, color = color, templateKey = templateKey, archived = archived)

fun transaction(
    id: String,
    type: TransactionType,
    accountId: String,
    amountMinor: Long,
    occurredOn: LocalDate,
    currency: String = "VND",
    categoryId: String? = null,
    payee: String? = null,
    counterAccountId: String? = null,
) = Transaction(
    id = id,
    type = type,
    status = TransactionStatus.CONFIRMED,
    accountId = accountId,
    counterAccountId = counterAccountId,
    amount = Money(amountMinor, currency),
    categoryId = categoryId,
    occurredOn = occurredOn,
    occurredAt = null,
    payee = payee,
    note = null,
)
