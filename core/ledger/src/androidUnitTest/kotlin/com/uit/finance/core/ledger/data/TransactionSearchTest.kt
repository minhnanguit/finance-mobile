package com.uit.finance.core.ledger.data

import app.cash.turbine.test
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.repository.AccountRepositoryImpl
import com.uit.finance.core.ledger.data.repository.BalanceRepositoryImpl
import com.uit.finance.core.ledger.data.repository.CategoryRepositoryImpl
import com.uit.finance.core.ledger.data.repository.TransactionRepositoryImpl
import com.uit.finance.core.ledger.data.sync.TransactionSyncApplier
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryDraft
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.ledger.support.FakeCodec
import com.uit.finance.core.ledger.support.FakeDatabases
import com.uit.finance.core.ledger.support.FakeScheduler
import com.uit.finance.core.ledger.support.ledgerStore
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.sync.engine.RemoteChange
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/** Tìm kiếm + lọc lịch sử (docs/SEARCH-PLAN.md Phase 1) trên SQLite thật, dữ liệu ghi qua đúng đường ghi của app. */
class TransactionSearchTest {

    private val databases = FakeDatabases()
    private val database: FinanceDatabase get() = databases.database.value!!
    private val codec = FakeCodec()
    private val ids = FakeUuidGenerator()
    private val clock = TestClock() // 2023-11-14

    private class Repos(
        val accounts: AccountRepositoryImpl,
        val categories: CategoryRepositoryImpl,
        val transactions: TransactionRepositoryImpl,
        val balances: BalanceRepositoryImpl,
    )

    private fun TestScope.repos(): Repos {
        val dispatchers = testDispatcherProvider()
        val store = ledgerStore(databases, FakeScheduler(), dispatchers)
        val outbox = OutboxWriter(ids, clock)
        return Repos(
            AccountRepositoryImpl(store, outbox, codec, ids, dispatchers),
            CategoryRepositoryImpl(store, outbox, codec, ids, dispatchers),
            TransactionRepositoryImpl(store, outbox, codec, ids, clock, dispatchers),
            BalanceRepositoryImpl(store, dispatchers),
        )
    }

    private fun <T> AppResult<T>.value(): T = (this as AppResult.Success).value

    /**
     * Ví VND (`wallet`, `bank`) + ví USD; "Ăn uống" có con "Cà phê"; mỗi giao dịch một đặc điểm riêng để từng điều
     * kiện lọc ra đúng tập mong đợi.
     */
    private inner class Seed(val repos: Repos) {
        lateinit var wallet: Account
        lateinit var bank: Account
        lateinit var usd: Account
        lateinit var food: Category
        lateinit var coffee: Category
        lateinit var transport: Category
        lateinit var salary: Category

        lateinit var latte: Transaction // Cà phê, ví, 10/11
        lateinit var lunch: Transaction // Ăn uống, ví, 12/11
        lateinit var grab: Transaction // Di chuyển, bank, 20/10
        lateinit var payday: Transaction // Thu, bank, 31/10
        lateinit var topUp: Transaction // Chuyển ví → bank, 01/11
        lateinit var draft: Transaction // Nháp, Ăn uống, ví, 13/11
        lateinit var starbucks: Transaction // Cà phê, ví USD, 05/11

        val all: List<Transaction> get() = listOf(latte, lunch, grab, payday, topUp, draft, starbucks)

        suspend fun create(): Seed = apply {
            wallet = account("Ví", "VND")
            bank = account("Bank", "VND")
            usd = account("Ví đô", "USD")
            food = category("Ăn uống", CategoryKind.EXPENSE)
            coffee = category("Cà phê", CategoryKind.EXPENSE, parentId = food.id)
            transport = category("Di chuyển", CategoryKind.EXPENSE)
            salary = category("Lương", CategoryKind.INCOME)

            latte = record(TransactionType.EXPENSE, wallet, 45_000, day(11, 10), coffee, payee = "Highlands Coffee", note = "Cà phê sữa đá")
            lunch = record(TransactionType.EXPENSE, wallet, 120_000, day(11, 12), food, payee = "Bún chả Hương Liên")
            grab = record(TransactionType.EXPENSE, bank, 30_000, day(10, 20), transport, payee = "Grab", note = "Đi làm")
            payday = record(TransactionType.INCOME, bank, 10_000_000, day(10, 31), salary, payee = "Công ty A")
            topUp = repos.transactions.record(
                TransactionDraft(TransactionType.TRANSFER, wallet.id, 500_000, day(11, 1), counterAccountId = bank.id, note = "Nạp tiền"),
            ).value()
            draft = record(TransactionType.EXPENSE, wallet, 60_000, day(11, 13), food, note = "Ăn trưa", status = TransactionStatus.DRAFT)
            starbucks = record(TransactionType.EXPENSE, usd, 550, day(11, 5), coffee, payee = "Starbucks")
        }

        private suspend fun account(name: String, currency: String) =
            repos.accounts.create(AccountDraft(name, AccountType.CASH, currency, 0)).value()

        private suspend fun category(name: String, kind: CategoryKind, parentId: String? = null) =
            repos.categories.create(CategoryDraft(kind, name, parentId = parentId)).value()

        suspend fun record(
            type: TransactionType,
            account: Account,
            amount: Long,
            on: LocalDate,
            category: Category,
            payee: String? = null,
            note: String? = null,
            status: TransactionStatus = TransactionStatus.CONFIRMED,
        ): Transaction = repos.transactions.record(
            TransactionDraft(type, account.id, amount, on, categoryId = category.id, status = status, payee = payee, note = note),
        ).value()
    }

    private fun day(month: Int, day: Int) = LocalDate(2023, month, day)

    private suspend fun Repos.seed() = Seed(this).create()

    private suspend fun Repos.search(filter: TransactionFilter, limit: Int = 100): List<Transaction> =
        transactions.observeFiltered(filter, limit).first()

    private suspend fun Repos.searchIds(filter: TransactionFilter): Set<String> = search(filter).map { it.id }.toSet()

    private fun ids(vararg transactions: Transaction): Set<String> = transactions.map { it.id }.toSet()

    // ---- Không lọc, phân trang ----

    @Test
    fun `không lọc gì thì ra mọi giao dịch - đúng thứ tự và cửa sổ LIMIT của observeRecent`() = runTest {
        val repos = repos()
        repos.seed()

        assertEquals(repos.transactions.observeRecent(100).first(), repos.search(TransactionFilter.ALL))
        assertEquals(repos.transactions.observeRecent(3).first(), repos.search(TransactionFilter.ALL, limit = 3))
    }

    @Test
    fun `tập rỗng và từ khoá trống đều là tất cả`() = runTest {
        val repos = repos()
        repos.seed()
        val empty = TransactionFilter(query = "   ", types = emptySet(), accountIds = emptySet(), categoryIds = emptySet())

        assertEquals(repos.search(TransactionFilter.ALL), repos.search(empty))
    }

    // ---- Từng điều kiện riêng ----

    @Test
    fun `lọc khoảng ngày gồm cả hai đầu - một đầu bỏ trống là không giới hạn`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        assertEquals(ids(seed.topUp, seed.starbucks, seed.latte), repos.searchIds(TransactionFilter(from = day(11, 1), to = day(11, 10))))
        assertEquals(ids(seed.grab, seed.payday), repos.searchIds(TransactionFilter(to = day(10, 31))))
        assertEquals(ids(seed.lunch, seed.draft), repos.searchIds(TransactionFilter(from = day(11, 12))))
    }

    @Test
    fun `lọc loại - một hoặc nhiều loại`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        assertEquals(ids(seed.payday), repos.searchIds(TransactionFilter(types = setOf(TransactionType.INCOME))))
        assertEquals(
            ids(seed.payday, seed.topUp),
            repos.searchIds(TransactionFilter(types = setOf(TransactionType.INCOME, TransactionType.TRANSFER))),
        )
    }

    @Test
    fun `lọc ví - chuyển tiền khớp cả ví gửi lẫn ví nhận`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        assertEquals(ids(seed.grab, seed.payday, seed.topUp), repos.searchIds(TransactionFilter(accountIds = setOf(seed.bank.id))))
        assertEquals(ids(seed.starbucks), repos.searchIds(TransactionFilter(accountIds = setOf(seed.usd.id))))
        assertEquals(
            ids(seed.latte, seed.lunch, seed.topUp, seed.draft, seed.starbucks),
            repos.searchIds(TransactionFilter(accountIds = setOf(seed.usd.id, seed.wallet.id))),
        )
    }

    @Test
    fun `lọc danh mục - chọn cha là gồm luôn con - chọn con thì chỉ con`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        assertEquals(
            ids(seed.latte, seed.lunch, seed.draft, seed.starbucks),
            repos.searchIds(TransactionFilter(categoryIds = setOf(seed.food.id))),
        )
        assertEquals(ids(seed.latte, seed.starbucks), repos.searchIds(TransactionFilter(categoryIds = setOf(seed.coffee.id))))
        assertEquals(
            ids(seed.grab, seed.payday),
            repos.searchIds(TransactionFilter(categoryIds = setOf(seed.transport.id, seed.salary.id))),
        )
    }

    @Test
    fun `lọc trạng thái chỉ nháp`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        assertEquals(ids(seed.draft), repos.searchIds(TransactionFilter(status = TransactionStatus.DRAFT)))
    }

    @Test
    fun `kết hợp nhiều điều kiện là AND`() = runTest {
        val repos = repos()
        val seed = repos.seed()
        val filter = TransactionFilter(
            query = "ca phe",
            from = day(11, 1),
            to = day(11, 30),
            types = setOf(TransactionType.EXPENSE),
            accountIds = setOf(seed.wallet.id),
            categoryIds = setOf(seed.food.id),
            status = TransactionStatus.CONFIRMED,
        )

        assertEquals(listOf(seed.latte), repos.search(filter))
    }

    // ---- Từ khoá ----

    @Test
    fun `tìm không phân biệt hoa thường và dấu - kể cả từ khoá dạng NFD`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        assertEquals(ids(seed.latte), repos.searchIds(TransactionFilter(query = "HIGHLANDS")))
        assertEquals(ids(seed.latte), repos.searchIds(TransactionFilter(query = "sua da")))
        assertEquals(ids(seed.lunch), repos.searchIds(TransactionFilter(query = "  Bún   chả ")))
        assertEquals(ids(seed.lunch), repos.searchIds(TransactionFilter(query = "hu\u031Bo\u031Bng lie\u0302n"))) // NFD
    }

    @Test
    fun `tìm theo tên danh mục cha ra cả giao dịch của danh mục con`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        assertEquals(ids(seed.latte, seed.lunch, seed.draft, seed.starbucks), repos.searchIds(TransactionFilter(query = "an uong")))
        assertEquals(ids(seed.latte, seed.starbucks), repos.searchIds(TransactionFilter(query = "Cà phê")))
        assertEquals(ids(seed.payday), repos.searchIds(TransactionFilter(query = "luong")))
    }

    @Test
    fun `ký tự đặc biệt của LIKE được tìm đúng chữ - không thành wildcard`() = runTest {
        val repos = repos()
        val seed = repos.seed()
        val percent = seed.record(TransactionType.EXPENSE, seed.wallet, 1_000, day(11, 2), seed.transport, note = "Giảm 50% cuối tuần")
        val fifty = seed.record(TransactionType.EXPENSE, seed.wallet, 1_000, day(11, 2), seed.transport, note = "Giảm 500 nghìn")
        val underscore = seed.record(TransactionType.EXPENSE, seed.wallet, 1_000, day(11, 2), seed.transport, note = "mã a_b")
        val letter = seed.record(TransactionType.EXPENSE, seed.wallet, 1_000, day(11, 2), seed.transport, note = "mã axb")
        val backslash = seed.record(TransactionType.EXPENSE, seed.wallet, 1_000, day(11, 2), seed.transport, note = "mã a\\b")

        assertEquals(ids(percent, fifty), repos.searchIds(TransactionFilter(query = "50")))
        assertEquals(ids(percent), repos.searchIds(TransactionFilter(query = "50%")))
        assertEquals(ids(underscore), repos.searchIds(TransactionFilter(query = "a_b")))
        assertEquals(ids(backslash), repos.searchIds(TransactionFilter(query = "a\\b")))
        assertTrue(letter.id !in repos.searchIds(TransactionFilter(query = "a_b")))
    }

    @Test
    fun `từ khoá dài hơn 100 ký tự bị cắt - không lỗi`() = runTest {
        val repos = repos()
        val seed = repos.seed()
        val long = seed.record(TransactionType.EXPENSE, seed.wallet, 1_000, day(11, 2), seed.transport, note = "x".repeat(100))

        assertEquals(ids(long), repos.searchIds(TransactionFilter(query = "x".repeat(150))))
    }

    // ---- Tự cập nhật + mọi đường ghi đều có search_text (F4) ----

    @Test
    fun `đổi tên danh mục khi đang tìm thì kết quả tự cập nhật`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        repos.transactions.observeFiltered(TransactionFilter(query = "do uong"), 100).test {
            assertEquals(emptyList(), awaitItem())

            repos.categories.update(seed.food.copy(name = "Đồ uống")).value()

            assertEquals(ids(seed.latte, seed.lunch, seed.draft, seed.starbucks), awaitItem().map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ghi giao dịch mới khớp bộ lọc thì danh sách tự cập nhật`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        repos.transactions.observeFiltered(TransactionFilter(query = "phuc long"), 100).test {
            assertEquals(emptyList(), awaitItem())

            val added = seed.record(TransactionType.EXPENSE, seed.wallet, 55_000, day(11, 14), seed.coffee, payee = "Phúc Long")

            assertEquals(listOf(added), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sửa ghi chú thì search_text đổi theo`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        repos.transactions.update(seed.grab.copy(note = "Về quê")).value()

        assertEquals(ids(seed.grab), repos.searchIds(TransactionFilter(query = "ve que")))
        assertEquals(emptySet(), repos.searchIds(TransactionFilter(query = "di lam")))
    }

    @Test
    fun `giao dịch kéo về từ server cũng tìm được`() = runTest {
        val repos = repos()
        val seed = repos.seed()
        val payload = TransactionPayload(
            "EXPENSE", "CONFIRMED", seed.wallet.id, null, 35_000, "VND", seed.coffee.id, day(11, 3), null, "Katinat", "Trà đào",
        )

        TransactionSyncApplier(codec).apply(database, RemoteChange("transaction", "remote-1", 1, deleted = false, data = codec.encode(payload)))

        assertEquals(setOf("remote-1"), repos.searchIds(TransactionFilter(query = "tra dao")))
    }

    // ---- Dòng tổng (F10) ----

    @Test
    fun `dòng tổng đếm mọi dòng đang hiện - gồm nháp và chuyển tiền`() = runTest {
        val repos = repos()
        val seed = repos.seed()

        val totals = repos.transactions.observeFilteredTotals(TransactionFilter.ALL).first()

        assertEquals(seed.all.size, totals.count)
        assertEquals(repos.search(TransactionFilter.ALL).size, totals.count)
        assertEquals(1, totals.draftCount)
    }

    @Test
    fun `kết quả chỉ có nháp thì count lớn hơn 0 nhưng tổng bằng 0`() = runTest {
        val repos = repos()
        repos.seed()

        val totals = repos.transactions.observeFilteredTotals(TransactionFilter(status = TransactionStatus.DRAFT)).first()

        assertEquals(1, totals.count)
        assertEquals(1, totals.draftCount)
        assertEquals(CashFlow.EMPTY, totals.cashFlow)
        assertEquals(0, totals.cashFlow.expense("VND").amountMinor)
    }

    @Test
    fun `chuyển tiền được đếm nhưng không vào tổng thu chi`() = runTest {
        val repos = repos()
        repos.seed()

        val totals = repos.transactions.observeFilteredTotals(TransactionFilter(types = setOf(TransactionType.TRANSFER))).first()

        assertEquals(1, totals.count)
        assertEquals(CashFlow.EMPTY, totals.cashFlow)
    }

    @Test
    fun `tổng tách theo tiền tệ - không cộng VND với USD`() = runTest {
        val repos = repos()
        repos.seed()

        val totals = repos.transactions.observeFilteredTotals(TransactionFilter(query = "ca phe")).first()

        assertEquals(setOf("USD", "VND"), totals.cashFlow.currencies)
        assertEquals(45_000, totals.cashFlow.expense("VND").amountMinor)
        assertEquals(550, totals.cashFlow.expense("USD").amountMinor)
    }

    @Test
    fun `chỉ lọc theo ngày thì dòng tổng bằng thu chi của Trang chủ`() = runTest {
        val repos = repos()
        repos.seed()
        val ranges = listOf(
            day(11, 1) to day(11, 30),
            day(10, 1) to day(10, 31),
            LedgerLimits.EARLIEST_DATE to day(11, 14),
            day(12, 1) to day(12, 31),
        )

        ranges.forEach { (from, to) ->
            val totals = repos.transactions.observeFilteredTotals(TransactionFilter(from = from, to = to)).first()
            assertEquals(repos.balances.observeCashFlow(from, to).first(), totals.cashFlow, "khoảng $from … $to")
        }
    }

    @Test
    fun `dòng tổng chính xác gần biên Long và kẹt ở biên khi vượt - không ném lỗi`() = runTest {
        val repos = repos()
        database.transaction {
            repeat(9_000) { upsert("near-$it", "EXPENSE", LedgerLimits.MAX_AMOUNT_MINOR) }
            repeat(3) { upsert("small-$it", "EXPENSE", 999_999_999) }
            repeat(10_000) { upsert("over-$it", "INCOME", LedgerLimits.MAX_AMOUNT_MINOR) } // 10¹⁹ > Long.MAX
        }

        val totals = repos.transactions.observeFilteredTotals(TransactionFilter.ALL).first()

        assertEquals(9_000 * LedgerLimits.MAX_AMOUNT_MINOR + 3 * 999_999_999L, totals.cashFlow.expense("VND").amountMinor)
        assertEquals(Long.MAX_VALUE, totals.cashFlow.income("VND").amountMinor)
        assertEquals(19_003, totals.count)
    }

    private fun upsert(id: String, type: String, amount: Long) = database.ledgerTransactionQueries.upsert(
        id, type, "CONFIRMED", "a", null, amount, "VND", "c", "2023-11-14", null, null, null, 0, "",
    )
}
