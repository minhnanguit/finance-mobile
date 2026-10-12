package com.uit.finance.core.ledger.data

import app.cash.turbine.test
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.repository.AccountRepositoryImpl
import com.uit.finance.core.ledger.data.repository.BalanceRepositoryImpl
import com.uit.finance.core.ledger.data.repository.CategoryRepositoryImpl
import com.uit.finance.core.ledger.data.repository.TransactionRepositoryImpl
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryDraft
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.support.FakeCodec
import com.uit.finance.core.ledger.support.FakeDatabases
import com.uit.finance.core.ledger.support.FakeScheduler
import com.uit.finance.core.ledger.support.ledgerStore
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class LedgerRepositoriesTest {

    private val databases = FakeDatabases()
    private val database: FinanceDatabase get() = databases.database.value!!
    private val scheduler = FakeScheduler()
    private val codec = FakeCodec()
    private val ids = FakeUuidGenerator()
    private val clock = TestClock() // 2023-11-14
    private val today = LocalDate(2023, 11, 14)

    private class Repos(
        val accounts: AccountRepositoryImpl,
        val categories: CategoryRepositoryImpl,
        val transactions: TransactionRepositoryImpl,
        val balances: BalanceRepositoryImpl,
    )

    private fun TestScope.repos(): Repos {
        val dispatchers = testDispatcherProvider()
        val store = ledgerStore(databases, scheduler, dispatchers)
        val outbox = OutboxWriter(ids, clock)
        return Repos(
            AccountRepositoryImpl(store, outbox, codec, ids, dispatchers),
            CategoryRepositoryImpl(store, outbox, codec, ids, dispatchers),
            TransactionRepositoryImpl(store, outbox, codec, ids, clock, dispatchers),
            BalanceRepositoryImpl(store, dispatchers),
        )
    }

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).value

    private fun AppResult<*>.fieldErrors(): List<FieldError> = ((this as AppResult.Failure).error as AppError.Validation).fieldErrors

    private fun outboxActions() = database.outboxQueries.selectAll().executeAsList().map { "${it.entity}:${it.action}" }

    private suspend fun Repos.wallet(currency: String = "VND", opening: Long = 0): Account =
        accounts.create(AccountDraft("Ví", AccountType.CASH, currency, opening)).value()

    private suspend fun Repos.category(kind: CategoryKind = CategoryKind.EXPENSE): Category =
        categories.create(CategoryDraft(kind, "Ăn uống")).value()

    private fun expense(account: Account, category: Category, amount: Long = 50_000) =
        TransactionDraft(TransactionType.EXPENSE, account.id, amount, today, categoryId = category.id)

    // ---- Ghi cùng outbox ----

    @Test
    fun `tạo ví ghi cả dòng ví lẫn op UPSERT trong outbox, rồi hẹn sync`() = runTest {
        val account = repos().wallet(opening = 100_000)

        assertEquals("Ví", database.accountQueries.selectById(account.id).executeAsOne().name)
        assertEquals(listOf("account:UPSERT"), outboxActions())
        assertEquals(1, scheduler.immediateRequests)
    }

    @Test
    fun `vi phạm luật giữa transaction thì không có dòng nào và không có op nào`() = runTest {
        val repos = repos()
        val other = repos.wallet(currency = "USD")
        val wallet = repos.wallet()
        val opsBefore = outboxActions()

        val result = repos.transactions.record(
            TransactionDraft(TransactionType.TRANSFER, wallet.id, 1_000, today, counterAccountId = other.id),
        )

        assertEquals(listOf(FieldError("counterAccountId", LedgerErrorCode.INVALID_TRANSFER)), result.fieldErrors())
        assertEquals(opsBefore, outboxActions())
        assertTrue(database.ledgerTransactionQueries.selectRecent(10).executeAsList().isEmpty())
    }

    @Test
    fun `chuyển tiền kèm phí ghi cả hai dòng trong một transaction`() = runTest {
        val repos = repos()
        val wallet = repos.wallet(opening = 1_000_000)
        val bank = repos.accounts.create(AccountDraft("Bank", AccountType.BANK, "VND", 0)).value()
        val fee = repos.category()

        val saved = repos.transactions.recordAll(
            listOf(
                TransactionDraft(TransactionType.TRANSFER, wallet.id, 500_000, today, counterAccountId = bank.id),
                expense(wallet, fee, 3_300),
            ),
        ).value()

        assertEquals(listOf(TransactionType.TRANSFER, TransactionType.EXPENSE), saved.map { it.type })
        assertEquals(2, database.ledgerTransactionQueries.selectRecent(10).executeAsList().size)
    }

    @Test
    fun `một dòng trong nhóm vi phạm luật thì không dòng nào được lưu`() = runTest {
        val repos = repos()
        val wallet = repos.wallet()
        val bank = repos.accounts.create(AccountDraft("Bank", AccountType.BANK, "VND", 0)).value()
        val income = repos.category(CategoryKind.INCOME)
        val opsBefore = outboxActions()

        val result = repos.transactions.recordAll(
            listOf(
                TransactionDraft(TransactionType.TRANSFER, wallet.id, 500_000, today, counterAccountId = bank.id),
                expense(wallet, income, 3_300), // danh mục THU cho khoản CHI
            ),
        )

        assertEquals(LedgerErrorCode.CATEGORY_KIND_MISMATCH, result.fieldErrors().single().message)
        assertTrue(database.ledgerTransactionQueries.selectRecent(10).executeAsList().isEmpty())
        assertEquals(opsBefore, outboxActions())
    }

    @Test
    fun `sắp xếp ví chỉ ghi ví đổi chỗ`() = runTest {
        val repos = repos()
        val first = repos.accounts.create(AccountDraft("A", AccountType.CASH, "VND", 0, sortOrder = 0)).value()
        val second = repos.accounts.create(AccountDraft("B", AccountType.CASH, "VND", 0, sortOrder = 1)).value()
        val third = repos.accounts.create(AccountDraft("C", AccountType.CASH, "VND", 0, sortOrder = 2)).value()
        val writesBefore = scheduler.immediateRequests

        repos.accounts.reorder(listOf(second.id, first.id, third.id)).value()

        val order = database.accountQueries.selectActive().executeAsList().map { it.id }
        assertEquals(listOf(second.id, first.id, third.id), order)
        assertEquals(writesBefore + 1, scheduler.immediateRequests)
    }

    @Test
    fun `chưa mở sổ của user nào thì ghi trả Storage`() = runTest {
        val repos = repos()
        databases.database.value = null

        val result = repos.accounts.create(AccountDraft("Ví", AccountType.CASH, "VND", 0))

        assertIs<AppError.Storage>((result as AppResult.Failure).error)
    }

    // ---- Luật (giống backend) ----

    @Test
    fun `kiểm field trước khi ghi - tên quá 50 ký tự, tiền tệ sai, số tiền 0`() = runTest {
        val repos = repos()

        val badAccount = repos.accounts.create(AccountDraft("x".repeat(51), AccountType.CASH, "vnd", 0))
        val wallet = repos.wallet()
        val food = repos.category()
        val zero = repos.transactions.record(expense(wallet, food, amount = 0))

        assertEquals(listOf("name", "currency"), badAccount.fieldErrors().map { it.field })
        assertEquals(listOf("amountMinor"), zero.fieldErrors().map { it.field })
    }

    @Test
    fun `tiền tệ của giao dịch luôn lấy theo ví`() = runTest {
        val repos = repos()
        val wallet = repos.wallet(currency = "USD")
        val food = repos.category()

        val transaction = repos.transactions.record(expense(wallet, food, amount = 2_500)).value()

        assertEquals(Money(2_500, "USD"), transaction.amount)
        assertEquals("USD", (codec.payloads.last() as TransactionPayload).currency)
    }

    @Test
    fun `danh mục sai loại bị chặn - ví và danh mục đã archive vẫn ghi được như server`() = runTest {
        val repos = repos()
        val wallet = repos.wallet()
        val salary = repos.category(CategoryKind.INCOME)
        val food = repos.category()

        val mismatch = repos.transactions.record(expense(wallet, salary))
        repos.accounts.setArchived(wallet.id, true)
        repos.categories.setArchived(food.id, true)
        val intoArchived = repos.transactions.record(expense(wallet, food))

        assertEquals(LedgerErrorCode.CATEGORY_KIND_MISMATCH, mismatch.fieldErrors().single().message)
        assertIs<AppResult.Success<*>>(intoArchived)
    }

    @Test
    fun `giao dịch cũ trong ví đã archive vẫn sửa được ghi chú`() = runTest {
        val repos = repos()
        val wallet = repos.wallet()
        val food = repos.category()
        val transaction = repos.transactions.record(expense(wallet, food)).value()
        repos.accounts.setArchived(wallet.id, true)

        val updated = repos.transactions.update(transaction.copy(note = "sửa ghi chú"))

        assertEquals("sửa ghi chú", updated.value().note)
    }

    @Test
    fun `ví đã có giao dịch thì khoá tiền tệ và không xoá được`() = runTest {
        val repos = repos()
        val wallet = repos.wallet()
        val food = repos.category()
        repos.transactions.record(expense(wallet, food))

        val currency = repos.accounts.update(wallet.copy(openingBalance = Money(0, "USD")))
        val delete = repos.accounts.delete(wallet.id)

        assertEquals(LedgerErrorCode.CURRENCY_LOCKED, currency.fieldErrors().single().message)
        assertEquals(LedgerErrorCode.IN_USE, delete.fieldErrors().single().message)
    }

    @Test
    fun `danh mục tối đa 2 cấp và không đổi được loại`() = runTest {
        val repos = repos()
        val food = repos.category()
        val coffee = repos.categories.create(CategoryDraft(CategoryKind.EXPENSE, "Cà phê", parentId = food.id)).value()

        val tooDeep = repos.categories.create(CategoryDraft(CategoryKind.EXPENSE, "Latte", parentId = coffee.id))
        val kind = repos.categories.update(food.copy(kind = CategoryKind.INCOME))

        assertEquals(LedgerErrorCode.INVALID_PARENT, tooDeep.fieldErrors().single().message)
        assertEquals(LedgerErrorCode.KIND_IMMUTABLE, kind.fieldErrors().single().message)
    }

    @Test
    fun `tối đa 50 ví`() = runTest {
        val repos = repos()
        repeat(50) { repos.wallet() }

        val result = repos.accounts.create(AccountDraft("Ví 51", AccountType.CASH, "VND", 0))

        assertEquals(LedgerErrorCode.LIMIT_EXCEEDED, result.fieldErrors().single().message)
    }

    @Test
    fun `người nhận và ghi chú rỗng được lưu là không có, giống backend`() = runTest {
        val repos = repos()
        val wallet = repos.wallet()
        val food = repos.category()

        val transaction = repos.transactions.record(expense(wallet, food).copy(payee = "   ", note = " phở ")).value()

        assertEquals(null, transaction.payee)
        assertEquals("phở", transaction.note)
    }

    // ---- Xoá ----

    @Test
    fun `xoá giao dịch đánh dấu tombstone và thêm op DELETE`() = runTest {
        val repos = repos()
        val wallet = repos.wallet()
        val food = repos.category()
        val transaction = repos.transactions.record(expense(wallet, food)).value()

        repos.transactions.delete(transaction.id)

        assertEquals(1L, database.ledgerTransactionQueries.selectById(transaction.id).executeAsOne().deleted)
        assertEquals("transaction:DELETE", outboxActions().last())
    }

    // ---- Số dư ----

    @Test
    fun `số dư - bỏ DRAFT và đã xoá, tính chuyển đi và chuyển đến, tổng bỏ ví archive`() = runTest {
        val repos = repos()
        val wallet = repos.wallet(opening = 100_000)
        val bank = repos.accounts.create(AccountDraft("Bank", AccountType.BANK, "VND", 0)).value()
        val hidden = repos.accounts.create(AccountDraft("Cũ", AccountType.CASH, "VND", 900_000)).value()
        val food = repos.category()
        val salary = repos.category(CategoryKind.INCOME)
        repos.transactions.record(expense(wallet, food, 30_000))
        repos.transactions.record(expense(wallet, food, 999).copy(status = TransactionStatus.DRAFT))
        repos.transactions.record(TransactionDraft(TransactionType.INCOME, wallet.id, 50_000, today, categoryId = salary.id))
        repos.transactions.record(TransactionDraft(TransactionType.TRANSFER, wallet.id, 20_000, today, counterAccountId = bank.id))
        val removed = repos.transactions.record(expense(wallet, food, 7_000)).value()
        repos.transactions.delete(removed.id)
        repos.accounts.setArchived(hidden.id, true)

        repos.balances.observeBalances().test {
            val summary = awaitItem()
            val byAccount = summary.accounts.associate { it.accountId to it.balance.amountMinor }
            assertEquals(100_000L, byAccount[wallet.id])
            assertEquals(20_000L, byAccount[bank.id])
            assertEquals(listOf(Money(120_000, "VND")), summary.totals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dòng tiền theo khoảng ngày - chỉ CONFIRMED trong kỳ, bỏ chuyển tiền, tách tiền tệ`() = runTest {
        val repos = repos()
        val wallet = repos.wallet()
        val bank = repos.accounts.create(AccountDraft("Bank", AccountType.BANK, "VND", 0)).value()
        val usd = repos.accounts.create(AccountDraft("USD", AccountType.BANK, "USD", 0)).value()
        val food = repos.category()
        val salary = repos.category(CategoryKind.INCOME)
        repos.transactions.record(expense(wallet, food, 30_000))
        repos.transactions.record(expense(wallet, food, 999).copy(status = TransactionStatus.DRAFT))
        repos.transactions.record(expense(wallet, food, 5_000).copy(occurredOn = today.minus(40, DateTimeUnit.DAY)))
        repos.transactions.record(TransactionDraft(TransactionType.INCOME, wallet.id, 50_000, today, categoryId = salary.id))
        repos.transactions.record(TransactionDraft(TransactionType.TRANSFER, wallet.id, 20_000, today, counterAccountId = bank.id))
        repos.transactions.record(TransactionDraft(TransactionType.EXPENSE, usd.id, 1_250, today, categoryId = food.id))
        val removed = repos.transactions.record(expense(wallet, food, 7_000)).value()
        repos.transactions.delete(removed.id)

        repos.balances.observeCashFlow(from = LocalDate(2023, 11, 1), to = LocalDate(2023, 11, 30)).test {
            val cashFlow = awaitItem()
            assertEquals(listOf(Money(50_000, "VND")), cashFlow.income)
            assertEquals(listOf(Money(1_250, "USD"), Money(30_000, "VND")), cashFlow.expense)
            assertEquals(Money(0, "USD"), cashFlow.income("USD"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `số dư tự cập nhật khi có giao dịch mới`() = runTest {
        val repos = repos()
        val wallet = repos.wallet(opening = 10_000)
        val food = repos.category()

        repos.balances.observeBalances().test {
            assertEquals(10_000L, awaitItem().accounts.single().balance.amountMinor)
            repos.transactions.record(expense(wallet, food, 4_000))
            assertEquals(6_000L, awaitItem().accounts.single().balance.amountMinor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `đổi user là các Flow đổi theo DB mới`() = runTest {
        val repos = repos()
        repos.wallet()

        repos.accounts.observeAccounts().test {
            assertEquals(1, awaitItem().size)
            databases.database.value = com.uit.finance.core.ledger.support.inMemoryDatabase()
            assertEquals(0, awaitItem().size)
            databases.database.value = null
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
