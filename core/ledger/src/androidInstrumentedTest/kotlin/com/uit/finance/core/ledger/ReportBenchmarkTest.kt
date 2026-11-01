package com.uit.finance.core.ledger

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.uit.finance.core.common.coroutines.DefaultDispatcherProvider
import com.uit.finance.core.database.DatabaseDriverFactory
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.data.repository.BalanceRepositoryImpl
import com.uit.finance.core.ledger.data.repository.SpendingRepositoryImpl
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.core.sync.scheduler.SyncScheduler
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Đo màn Báo cáo trên **SQLCipher Android thật**, 50.000 giao dịch (docs/REPORTS-PLAN.md Phase 3). Chạy bằng
 * `make bench` (cần máy ảo), kết quả ở logcat tag `ReportBenchmark`. Mục tiêu: mở báo cáo < 300 ms, kể cả khi sync
 * đang pull; trượt thì làm `monthly_totals` (plan mục 5).
 *
 * "Mở báo cáo" = **mọi** câu mà `ObserveReportOverviewUseCase` chạy cho một kỳ, chạy **nối tiếp** (app chạy song song
 * qua combine, nên số thật thấp hơn): thu/chi của kỳ, tổng theo danh mục, hai khoảng so sánh, xu hướng theo tháng.
 * Log chỉ có tên tình huống và thời gian (B8).
 */
@RunWith(AndroidJUnit4::class)
class ReportBenchmarkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = DatabaseDriverFactory(context)
    private val report = mutableListOf<String>()
    private val misses = mutableListOf<String>()

    @After
    fun cleanUp() = files.delete(FILE)

    @Test
    fun reportOn50kTransactionsMeetsTargets() {
        files.delete(FILE)
        val driver = files.create(FILE, KEY)
        val database = FinanceDatabase(driver)
        seed(database)

        val dispatchers = DefaultDispatcherProvider()
        val provider = object : UserDatabaseProvider {
            override val database = MutableStateFlow<FinanceDatabase?>(database)
        }
        val store = LedgerStore(provider, NoScheduler, dispatchers)
        val spending = SpendingRepositoryImpl(store, dispatchers)
        val balances = BalanceRepositoryImpl(store, dispatchers)

        val month = ReportPeriod.Month(YearMonth(2026, 10))
        val year = ReportPeriod.Year(2025)

        suspend fun open(period: ReportPeriod, trendFrom: YearMonth, trendTo: YearMonth) {
            val previous = period.previous()
            balances.observeCashFlow(period.start, period.end).first()
            spending.observeCategoryTotals(period.start, period.end, CategoryKind.EXPENSE, groupByParent = true).first()
            balances.observeCashFlow(period.start, period.end).first()
            balances.observeCashFlow(previous.start, previous.end).first()
            spending.observeMonthlyTotals(trendFrom, trendTo).first()
        }

        runBlocking {
            record("tổng theo danh mục: 1 tháng", median { spending.observeCategoryTotals(month.start, month.end, CategoryKind.EXPENSE, true).first() })
            record("tổng theo danh mục: 1 năm", median { spending.observeCategoryTotals(year.start, year.end, CategoryKind.EXPENSE, true).first() })
            record("danh mục con: 1 năm", median { spending.observeCategoryTotals(year.start, year.end, CategoryKind.EXPENSE, false).first() })
            record("xu hướng: 6 tháng", median { spending.observeMonthlyTotals(YearMonth(2026, 5), YearMonth(2026, 10)).first() })
            record("xu hướng: 12 tháng", median { spending.observeMonthlyTotals(YearMonth(2025, 1), YearMonth(2025, 12)).first() })
            record("ngân sách: đã chi 1 tháng", median { spending.observeMonthlyExpense(month.month).first() })
            record("MỞ BÁO CÁO: tháng", median { open(month, month.month.minus(5, DateTimeUnit.MONTH), month.month) }, TARGET_MS)
            record("MỞ BÁO CÁO: năm", median { open(year, YearMonth(2025, 1), YearMonth(2025, 12)) }, TARGET_MS)

            // Sync đang pull: một luồng khác ghi liên tục từng lô trong transaction, giống SyncEngine áp trang 500 dòng.
            val writer = thread(name = "fake-sync-pull") { simulatePull(database) }
            record("MỞ BÁO CÁO: năm, khi sync đang ghi", median { open(year, YearMonth(2025, 1), YearMonth(2025, 12)) }, TARGET_MS)
            writer.join()
        }
        driver.close()

        val summary = report.joinToString("\n")
        Log.i(TAG, "\n$summary")
        assertTrue("Trượt mục tiêu: $misses\n$summary", misses.isEmpty())
    }

    /** 50k giao dịch rải 2021 → 10/2026: 85% chi, 10% thu, còn lại chuyển tiền; vài phần trăm không có danh mục. */
    private fun seed(database: FinanceDatabase) {
        val random = Random(SEED)
        database.transaction {
            database.accountQueries.upsert("wallet", "Ví", "CASH", "VND", 0, 0, 0, 0)
            database.accountQueries.upsert("bank", "Ngân hàng", "BANK", "VND", 0, 1, 0, 0)
            repeat(PARENTS) { parent ->
                database.categoryQueries.upsert("cat-$parent", "EXPENSE", "Nhóm $parent", null, null, null, null, 0, 0)
                repeat(CHILDREN) { child ->
                    database.categoryQueries.upsert("cat-$parent-$child", "EXPENSE", "Con $child", "cat-$parent", null, null, null, 0, 0)
                }
            }
        }
        (0 until COUNT).chunked(SEED_BATCH).forEach { batch ->
            database.transaction {
                batch.forEach { index ->
                    val roll = random.nextInt(100)
                    val type = when {
                        roll < 85 -> "EXPENSE"
                        roll < 95 -> "INCOME"
                        else -> "TRANSFER"
                    }
                    val categoryId = when {
                        type == "TRANSFER" || random.nextInt(100) < 3 -> null
                        else -> "cat-${random.nextInt(PARENTS)}-${random.nextInt(CHILDREN)}"
                    }
                    database.ledgerTransactionQueries.upsert(
                        "t-$index", type, "CONFIRMED", "wallet", if (type == "TRANSFER") "bank" else null,
                        1_000L + random.nextLong(5_000_000), "VND", categoryId,
                        FIRST_DAY.plus(index * DAYS / COUNT, DateTimeUnit.DAY).toString(), null, null, null, 0, null,
                    )
                }
            }
        }
    }

    /** 20 lô × 500 dòng, mỗi lô một transaction: cỡ một lần pull lớn. */
    private fun simulatePull(database: FinanceDatabase) = repeat(PULL_BATCHES) { batch ->
        database.transaction {
            repeat(PULL_BATCH_SIZE) { index ->
                database.ledgerTransactionQueries.upsert(
                    "pull-$batch-$index", "EXPENSE", "CONFIRMED", "wallet", null, 10_000, "VND", "cat-0-0",
                    "2025-06-15", null, null, null, 0, null,
                )
            }
        }
    }

    private fun record(name: String, duration: Duration, target: Long? = null) {
        val millis = duration.toDouble(DurationUnit.MILLISECONDS)
        val verdict = when {
            target == null -> ""
            millis <= target -> "  ✓ < $target ms"
            else -> "  ✗ > $target ms".also { misses += name }
        }
        report += "%-45s %8.1f ms%s".format(name, millis, verdict)
    }

    /** Trung vị của [RUNS] lần đo sau [WARMUPS] lần chạy nóng máy. */
    private suspend fun median(block: suspend () -> Any): Duration {
        repeat(WARMUPS) { block() }
        return List(RUNS) { measureTime { block() } }.sorted()[RUNS / 2]
    }

    private object NoScheduler : SyncScheduler {
        override fun schedulePeriodic() = Unit
        override fun requestImmediate() = Unit
        override fun cancelAll() = Unit
    }

    private companion object {
        const val TAG = "ReportBenchmark"
        const val FILE = "bench-report.db"
        const val KEY = "bench-only-key"
        const val COUNT = 50_000
        const val SEED = 7
        const val SEED_BATCH = 5_000
        const val WARMUPS = 2
        const val RUNS = 7
        const val PULL_BATCHES = 20
        const val PULL_BATCH_SIZE = 500
        const val PARENTS = 10
        const val CHILDREN = 3
        const val TARGET_MS = 300L
        val FIRST_DAY = LocalDate(2021, 1, 1)
        const val DAYS = 2_120 // 2021-01-01 → 2026-10-22
    }
}
