@file:OptIn(ExperimentalTime::class)

package com.uit.finance.app.devtools

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryDraft
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.testing.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

class DemoDataSeederTest {

    /** Giữa trưa UTC nên `today()` giống nhau ở mọi múi giờ dev/CI (UTC, UTC+7). */
    private val clock = TestClock(Instant.fromEpochSeconds(1_699_963_200L)) // 2023-11-14T12:00:00Z
    private val accounts = InMemoryAccounts()
    private val categories = InMemoryCategories()
    private val transactions = InMemoryTransactions()
    private val seeder = DemoDataSeeder(accounts, categories, transactions, clock)

    @Test
    fun `seed tạo đủ ví và giao dịch trỏ đúng ví và danh mục`() = runTest {
        categories.withDefaults()

        val result = seeder.seed()

        assertEquals(LedgerCounts(DemoAccount.entries.size, DemoTransactions.size), assertIs<AppResult.Success<LedgerCounts>>(result).value)
        val accountIds = accounts.items.value.map { it.id }.toSet()
        val categoryIds = categories.items.value.map { it.id }.toSet()
        transactions.drafts.forEach { draft ->
            assertTrue(draft.accountId in accountIds)
            if (draft.type == TransactionType.TRANSFER) {
                assertTrue(draft.counterAccountId in accountIds)
            } else {
                assertTrue(draft.categoryId in categoryIds)
            }
            assertTrue(draft.occurredAt == null || draft.occurredAt!! <= clock.now())
        }
    }

    @Test
    fun `seed chọn danh mục theo icon và tính ngày lùi từ hôm nay`() = runTest {
        categories.withDefaults()

        seeder.seed()

        val today = clock.today()
        val salary = transactions.drafts.first { it.type == TransactionType.INCOME && it.payee == "Công ty TNHH ABC" }
        assertEquals("payments", categories.items.value.first { it.id == salary.categoryId }.icon)
        assertEquals(today.minus(10, DateTimeUnit.DAY), salary.occurredOn)
        assertTrue(transactions.drafts.any { it.occurredOn == today })
        assertTrue(transactions.drafts.any { it.status == TransactionStatus.DRAFT })
    }

    @Test
    fun `seed từ chối khi sổ đã có ví để không nhân đôi dữ liệu`() = runTest {
        categories.withDefaults()
        seeder.seed()
        val created = transactions.drafts.size

        val again = seeder.seed()

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(again).error)
        assertEquals(created, transactions.drafts.size)
    }

    @Test
    fun `seed báo lỗi khi chưa sync về danh mục mặc định`() = runTest {
        val result = seeder.seed()

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(result).error)
        assertTrue(accounts.items.value.isEmpty())
    }

    @Test
    fun `clear xoá mọi giao dịch rồi mọi ví - giữ danh mục`() = runTest {
        categories.withDefaults()
        seeder.seed()

        val result = seeder.clear()

        assertEquals(LedgerCounts(DemoAccount.entries.size, DemoTransactions.size), assertIs<AppResult.Success<LedgerCounts>>(result).value)
        assertTrue(transactions.items.value.isEmpty())
        assertTrue(accounts.items.value.isEmpty())
        assertTrue(categories.items.value.isNotEmpty())
    }
}

// ---- Fake trong bộ nhớ: chỉ đủ cho seeder ----

private fun notUsed(): Nothing = error("seeder không dùng tới")

private class InMemoryAccounts : AccountRepository {
    val items = MutableStateFlow<List<Account>>(emptyList())

    override fun observeAccounts(): Flow<List<Account>> = items

    override suspend fun create(draft: AccountDraft): AppResult<Account> {
        val account = Account(
            id = "acc-${items.value.size}",
            name = draft.name,
            type = draft.type,
            openingBalance = Money(draft.openingBalanceMinor, draft.currency),
            sortOrder = draft.sortOrder,
            archived = false,
        )
        items.value += account
        return AppResult.Success(account)
    }

    override suspend fun delete(accountId: String): AppResult<Unit> {
        items.value = items.value.filterNot { it.id == accountId }
        return AppResult.Success(Unit)
    }

    override suspend fun update(account: Account): AppResult<Account> = notUsed()
    override suspend fun setArchived(accountId: String, archived: Boolean): AppResult<Unit> = notUsed()
    override suspend fun reorder(orderedIds: List<String>): AppResult<Unit> = notUsed()
}

private class InMemoryCategories : CategoryRepository {
    val items = MutableStateFlow<List<Category>>(emptyList())

    /** Giống một phần danh mục mặc định server tạo ở lần sync đầu (icon theo `CategoryIcons.kt`). */
    fun withDefaults() {
        val expense = listOf("restaurant", "directions_car", "shopping_bag", "receipt", "home", "movie", "school")
        val income = listOf("payments", "redeem", "trending_up")
        items.value = expense.map { category(CategoryKind.EXPENSE, it) } + income.map { category(CategoryKind.INCOME, it) }
    }

    private fun category(kind: CategoryKind, icon: String) =
        Category(id = "cat-$icon", kind = kind, name = icon, parentId = null, icon = icon, color = null, templateKey = icon, archived = false)

    override fun observeCategories(): Flow<List<Category>> = items
    override suspend fun create(draft: CategoryDraft): AppResult<Category> = notUsed()
    override suspend fun update(category: Category): AppResult<Category> = notUsed()
    override suspend fun setArchived(categoryId: String, archived: Boolean): AppResult<Unit> = notUsed()
    override suspend fun delete(categoryId: String): AppResult<Unit> = notUsed()
}

private class InMemoryTransactions : TransactionRepository {
    val drafts = mutableListOf<TransactionDraft>()
    val items = MutableStateFlow<List<Transaction>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<Transaction>> = items.map { it.take(limit) }

    override suspend fun record(draft: TransactionDraft): AppResult<Transaction> {
        drafts += draft
        val transaction = Transaction(
            id = "tx-${drafts.size}",
            type = draft.type,
            status = draft.status,
            accountId = draft.accountId,
            counterAccountId = draft.counterAccountId,
            amount = Money(draft.amountMinor, "VND"),
            categoryId = draft.categoryId,
            occurredOn = draft.occurredOn,
            occurredAt = draft.occurredAt,
            payee = draft.payee,
            note = draft.note,
        )
        items.value += transaction
        return AppResult.Success(transaction)
    }

    override suspend fun delete(transactionId: String): AppResult<Unit> {
        items.value = items.value.filterNot { it.id == transactionId }
        return AppResult.Success(Unit)
    }

    override fun observe(transactionId: String): Flow<Transaction?> = notUsed()
    override suspend fun update(transaction: Transaction): AppResult<Transaction> = notUsed()
    override suspend fun recordAll(drafts: List<TransactionDraft>): AppResult<List<Transaction>> = notUsed()
}
