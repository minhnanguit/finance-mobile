package com.uit.finance.core.ledger.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.ledger.data.repository.TransactionRepositoryImpl
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.support.FakeCodec
import com.uit.finance.core.ledger.support.FakeDatabases
import com.uit.finance.core.ledger.support.FakeScheduler
import com.uit.finance.core.ledger.support.PlanRecordingDriver
import com.uit.finance.core.ledger.support.ledgerStore
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * F2 (docs/SEARCH-PLAN.md): câu lọc phải đi theo `ledger_transaction_occurred_idx` để trang đầu dừng ngay khi đủ
 * LIMIT, **kể cả khi không lọc ngày**. Lấy plan của đúng câu SQL mà repository chạy, với đúng tham số đã bind.
 */
class LedgerSearchQueryPlanTest {

    private val driver = PlanRecordingDriver(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
    private val database = FinanceDatabase(driver).also { FinanceDatabase.Schema.create(driver) }

    private fun planOf(filter: TransactionFilter): List<String> = runTest {
        val dispatchers = testDispatcherProvider()
        val ids = FakeUuidGenerator()
        val repository = TransactionRepositoryImpl(
            ledgerStore(FakeDatabases(database), FakeScheduler(), dispatchers),
            OutboxWriter(ids, TestClock()),
            FakeCodec(),
            ids,
            TestClock(),
            dispatchers,
        )
        driver.plans.clear()
        repository.observeFiltered(filter, limit = 50).first()
    }.let { driver.plans.filterKeys { it.contains("ORDER BY occurred_on DESC") }.values.single() }

    private fun assertWalksDateIndex(plan: List<String>) {
        assertTrue(plan.any { it.contains("USING INDEX ledger_transaction_occurred_idx") }, plan.toString())
        // Sắp toàn bộ kết quả (thay vì chỉ sắp trong từng ngày) nghĩa là phải đọc hết mọi dòng khớp trước khi LIMIT.
        assertFalse(plan.any { it.contains("TEMP B-TREE FOR ORDER BY") }, plan.toString())
    }

    @Test
    fun `không lọc gì vẫn đi theo index ngày`() {
        assertWalksDateIndex(planOf(TransactionFilter.ALL))
    }

    @Test
    fun `lọc ngày đi theo index ngày`() {
        assertWalksDateIndex(planOf(TransactionFilter(from = LocalDate(2023, 11, 1), to = LocalDate(2023, 11, 30))))
    }

    @Test
    fun `có từ khoá và đủ mọi điều kiện vẫn đi theo index ngày - không đổi sang index ví hay danh mục`() {
        val filter = TransactionFilter(
            query = "ca phe",
            types = setOf(TransactionType.EXPENSE),
            accountIds = setOf("a1", "a2"),
            categoryIds = setOf("c1"),
            status = TransactionStatus.CONFIRMED,
        )

        assertWalksDateIndex(planOf(filter))
    }
}
