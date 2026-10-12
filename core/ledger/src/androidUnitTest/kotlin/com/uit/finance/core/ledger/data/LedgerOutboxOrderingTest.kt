@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.ledger.data

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.repository.AccountRepositoryImpl
import com.uit.finance.core.ledger.data.repository.CategoryRepositoryImpl
import com.uit.finance.core.ledger.data.repository.TransactionRepositoryImpl
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.CategoryDraft
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.support.FakeCodec
import com.uit.finance.core.ledger.support.FakeDatabases
import com.uit.finance.core.ledger.support.FakeScheduler
import com.uit.finance.core.ledger.support.ledgerStore
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * Review Phase 4 (Codex #1): offline, tạo ví → ghi giao dịch → archive ví. Server áp từng op theo thứ tự
 * và không cho gắn giao dịch mới vào ví đã archive, nên archive phải lên server **sau** giao dịch.
 */
class LedgerOutboxOrderingTest {

    private val databases = FakeDatabases()
    private val database: FinanceDatabase get() = databases.database.value!!
    private val ids = FakeUuidGenerator()
    private val clock = TestClock() // 2023-11-14
    private val today = LocalDate(2023, 11, 14)

    private class Repos(
        val accounts: AccountRepositoryImpl,
        val categories: CategoryRepositoryImpl,
        val transactions: TransactionRepositoryImpl,
    )

    private fun TestScope.repos(): Repos {
        val dispatchers = testDispatcherProvider()
        val store = ledgerStore(databases, FakeScheduler(), dispatchers)
        val outbox = OutboxWriter(ids, clock)
        val codec = FakeCodec()
        return Repos(
            AccountRepositoryImpl(store, outbox, codec, ids, dispatchers),
            CategoryRepositoryImpl(store, outbox, codec, ids, dispatchers),
            TransactionRepositoryImpl(store, outbox, codec, ids, clock, dispatchers),
        )
    }

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).value

    /** Batch kế tiếp mà engine sẽ gửi, dạng `entity:id`. */
    private fun nextBatch(): List<String> =
        database.outboxQueries.selectEligible(now = clock.now().toEpochMilliseconds(), limit = 100)
            .executeAsList().map { "${it.entity}:${it.entity_id}" }

    /** Server nhận xong cả batch. */
    private fun serverApplies(batch: List<String>) {
        val rows = database.outboxQueries.selectAll().executeAsList()
        batch.forEach { key -> database.outboxQueries.deleteByOpId(rows.first { "${it.entity}:${it.entity_id}" == key }.op_id) }
    }

    private fun refsOf(entityId: String): List<String> {
        val seq = database.outboxQueries.selectAll().executeAsList().first { it.entity_id == entityId }.seq
        return database.outboxQueries.selectRefs(seq).executeAsList().map { "${it.entity}:${it.entity_id}" }
    }

    @Test
    fun `archive ví lên server sau giao dịch ghi trước đó vào ví`() = runTest {
        val repos = repos()
        val wallet = repos.accounts.create(AccountDraft("Ví", AccountType.CASH, "VND", 0)).value()
        val food = repos.categories.create(CategoryDraft(CategoryKind.EXPENSE, "Ăn uống")).value()
        val coffee = repos.transactions.record(
            TransactionDraft(TransactionType.EXPENSE, wallet.id, 30_000, today, categoryId = food.id),
        ).value()
        repos.accounts.setArchived(wallet.id, archived = true)

        val first = nextBatch()
        assertEquals(listOf("account:${wallet.id}", "category:${food.id}", "transaction:${coffee.id}"), first)
        serverApplies(first)
        assertEquals(listOf("account:${wallet.id}"), nextBatch(), "archive đi sau, ở batch riêng")
    }

    @Test
    fun `op giao dịch ghi lại ví, ví đến và danh mục mà nó trỏ tới`() = runTest {
        val repos = repos()
        val wallet = repos.accounts.create(AccountDraft("Ví", AccountType.CASH, "VND", 0)).value()
        val bank = repos.accounts.create(AccountDraft("Bank", AccountType.BANK, "VND", 0)).value()
        val food = repos.categories.create(CategoryDraft(CategoryKind.EXPENSE, "Ăn uống")).value()

        val transfer = repos.transactions.record(
            TransactionDraft(TransactionType.TRANSFER, wallet.id, 20_000, today, counterAccountId = bank.id),
        ).value()
        val lunch = repos.transactions.record(
            TransactionDraft(TransactionType.EXPENSE, wallet.id, 50_000, today, categoryId = food.id),
        ).value()

        assertEquals(listOf("account:${bank.id}", "account:${wallet.id}").sorted(), refsOf(transfer.id).sorted())
        assertEquals(listOf("account:${wallet.id}", "category:${food.id}").sorted(), refsOf(lunch.id).sorted())
    }

    @Test
    fun `danh mục con ghi lại danh mục cha`() = runTest {
        val repos = repos()
        val food = repos.categories.create(CategoryDraft(CategoryKind.EXPENSE, "Ăn uống")).value()

        val coffee = repos.categories.create(CategoryDraft(CategoryKind.EXPENSE, "Cà phê", parentId = food.id)).value()

        assertEquals(listOf("category:${food.id}"), refsOf(coffee.id))
    }
}
