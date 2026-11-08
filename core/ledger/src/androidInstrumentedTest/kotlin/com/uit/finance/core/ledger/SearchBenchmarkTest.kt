package com.uit.finance.core.ledger

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.uit.finance.core.common.coroutines.DefaultDispatcherProvider
import com.uit.finance.core.common.text.searchTextOf
import com.uit.finance.core.database.DatabaseDriverFactory
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.data.repository.TransactionRepositoryImpl
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.network.api.model.AccountPayload
import com.uit.finance.core.network.api.model.CategoryPayload
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.sync.outbox.OutboxWriter
import com.uit.finance.core.sync.scheduler.SyncScheduler
import com.uit.finance.core.testing.FakeUuidGenerator
import com.uit.finance.core.testing.TestClock
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
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Đo tìm kiếm trên **SQLCipher Android thật**, 50.000 giao dịch (docs/SEARCH-PLAN.md Phase 3). Không phải unit test:
 * cần máy ảo hoặc máy thật, chạy bằng `make bench`, kết quả ghi ở logcat tag `SearchBenchmark`. Trượt mục tiêu thì
 * test fail kèm bảng số đo; theo plan, khi đó làm FTS5 (mục 7) trước khi merge.
 *
 * Log chỉ có tên tình huống và thời gian, không có từ khoá hay số tiền (B8).
 */
@RunWith(AndroidJUnit4::class)
class SearchBenchmarkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = DatabaseDriverFactory(context)
    private val report = mutableListOf<String>()
    private val misses = mutableListOf<String>()

    @After
    fun cleanUp() = files.delete(FILE)

    @Test
    fun searchOn50kTransactionsMeetsTargets() {
        files.delete(FILE)
        seedLegacyDatabase()

        // Migration 2 → 3 + backfill chạy ở câu lệnh đầu tiên sau khi mở (openVerified), trừ đi thời gian mở thường
        // (dẫn khoá SQLCipher) để chỉ còn phần của migration.
        val (migrated, migrateTime) = timed { files.create(FILE, KEY).also { it.countMissing() } }
        assertEquals("backfill phải điền đủ mọi dòng", 0L, migrated.countMissing())
        migrated.close()
        val (reopened, openTime) = timed { files.create(FILE, KEY).also { it.countMissing() } }
        record("open (dẫn khoá SQLCipher)", openTime)
        record("migrate 2→3 + backfill 50k", migrateTime - openTime, target = 3_000)
        record("journal_mode = ${reopened.journalMode()}", Duration.ZERO)

        val database = FinanceDatabase(reopened)
        val repository = repository(database)
        val thisMonth = TransactionFilter(from = LocalDate(2026, 10, 1), to = LocalDate(2026, 10, 31))
        val cases = listOf(
            Case("trang đầu, không lọc", TransactionFilter.ALL, target = 100),
            Case("tháng này", thisMonth),
            Case("từ hay gặp (dừng sớm nhờ LIMIT)", TransactionFilter(query = "grab")),
            Case("từ trung bình trong ghi chú", TransactionFilter(query = "an trua")),
            Case("tên danh mục cha (gồm con)", TransactionFilter(query = "an uong")),
            Case("từ hiếm (quét hết bảng)", TransactionFilter(query = RARE_QUERY), target = 300),
            Case("lọc danh mục cha", TransactionFilter(categoryIds = setOf(category(0)))),
            Case("từ hiếm + tháng này", thisMonth.copy(query = RARE_QUERY)),
        )
        runBlocking {
            cases.forEach { case -> record("list: ${case.name}", median { repository.observeFiltered(case.filter, PAGE).first() }, case.target) }
            record("list: cuộn sâu (LIMIT 1000)", median { repository.observeFiltered(TransactionFilter.ALL, 1_000).first() })
            record("tổng: tháng này", median { repository.observeFilteredTotals(thisMonth).first() })
            record("tổng: từ hay gặp", median { repository.observeFilteredTotals(TransactionFilter(query = "grab")).first() })
            record("tổng: từ hiếm", median { repository.observeFilteredTotals(TransactionFilter(query = RARE_QUERY)).first() })

            assertEquals(1, repository.observeFiltered(TransactionFilter(query = RARE_QUERY), PAGE).first().size)

            // Sync đang pull: một luồng khác ghi liên tục từng lô trong transaction, giống SyncEngine áp trang 500 dòng.
            val writer = thread(name = "fake-sync-pull") { simulatePull(database) }
            record("list: từ hiếm khi sync đang ghi", median { repository.observeFiltered(TransactionFilter(query = RARE_QUERY), PAGE).first() }, target = 300)
            writer.join()
        }
        reopened.close()

        val summary = report.joinToString("\n")
        Log.i(TAG, "\n$summary")
        assertTrue("Trượt mục tiêu: $misses\n$summary", misses.isEmpty())
    }

    // ---- Dữ liệu ----

    /** 50k giao dịch rải 2021 → 10/2026, `search_text` NULL như DB của bản trước, rồi hạ về schema 2. */
    private fun seedLegacyDatabase() {
        val driver = files.create(FILE, KEY)
        val database = FinanceDatabase(driver)
        val random = Random(SEED)
        database.transaction {
            database.accountQueries.upsert("wallet", "Ví", "CASH", "VND", 0, 0, 0, 0)
            database.accountQueries.upsert("bank", "Ngân hàng", "BANK", "VND", 0, 1, 0, 0)
            repeat(PARENTS) { parent ->
                database.categoryQueries.upsert(category(parent), "EXPENSE", PARENT_NAMES[parent], null, null, null, null, 0, 0)
                repeat(CHILDREN) { child ->
                    database.categoryQueries.upsert(category(parent, child), "EXPENSE", "${PARENT_NAMES[parent]} $child", category(parent), null, null, null, 0, 0)
                }
            }
        }
        (0 until COUNT).chunked(SEED_BATCH).forEach { batch ->
            database.transaction {
                batch.forEach { index ->
                    val rare = index == COUNT / 2
                    database.ledgerTransactionQueries.upsert(
                        "t-${index.toString().padStart(6, '0')}", "EXPENSE", "CONFIRMED", if (index % 3 == 0) "bank" else "wallet",
                        null, 1_000L + random.nextLong(5_000_000), "VND",
                        category(random.nextInt(PARENTS), random.nextInt(CHILDREN)),
                        FIRST_DAY.plus(index * DAYS / COUNT, DateTimeUnit.DAY).toString(), null,
                        PAYEES[random.nextInt(PAYEES.size)],
                        if (rare) "Sửa xe Kawasaki ở tiệm quen" else NOTES[random.nextInt(NOTES.size)],
                        0, null,
                    )
                }
            }
        }
        driver.execute(null, "ALTER TABLE ledger_transaction DROP COLUMN search_text", 0)
        driver.execute(null, "PRAGMA user_version = 2", 0)
        driver.close()
    }

    /** 20 lô × 500 dòng, mỗi lô một transaction: cỡ một lần pull lớn. */
    private fun simulatePull(database: FinanceDatabase) = repeat(PULL_BATCHES) { batch ->
        database.transaction {
            repeat(PULL_BATCH_SIZE) { index ->
                val payee = PAYEES[index % PAYEES.size]
                database.ledgerTransactionQueries.upsert(
                    "pull-$batch-$index", "EXPENSE", "CONFIRMED", "wallet", null, 10_000, "VND", category(0, 0),
                    "2026-10-15", null, payee, null, 0, searchTextOf(payee, null),
                )
            }
        }
    }

    // ---- Đo ----

    private class Case(val name: String, val filter: TransactionFilter, val target: Long? = null)

    private fun record(name: String, duration: Duration, target: Long? = null) {
        val millis = duration.toDouble(DurationUnit.MILLISECONDS)
        val verdict = when {
            target == null -> ""
            millis <= target -> "  ✓ < $target ms"
            else -> "  ✗ > $target ms".also { misses += name }
        }
        report += "%-45s %8.1f ms%s".format(name, millis, verdict)
    }

    private inline fun <T> timed(block: () -> T): Pair<T, Duration> {
        var result: T
        val duration = measureTime { result = block() }
        return result to duration
    }

    /** Trung vị của [RUNS] lần đo sau [WARMUPS] lần chạy nóng máy. */
    private suspend fun median(block: suspend () -> Any): Duration {
        repeat(WARMUPS) { block() }
        return List(RUNS) { measureTime { block() } }.sorted()[RUNS / 2]
    }

    private fun repository(database: FinanceDatabase): TransactionRepositoryImpl {
        val dispatchers = DefaultDispatcherProvider()
        val provider = object : UserDatabaseProvider {
            override val database = MutableStateFlow<FinanceDatabase?>(database)
        }
        val scheduler = object : SyncScheduler {
            override fun schedulePeriodic() = Unit
            override fun requestImmediate() = Unit
            override fun cancelAll() = Unit
        }
        val ids = FakeUuidGenerator()
        return TransactionRepositoryImpl(
            store = LedgerStore(provider, scheduler, dispatchers),
            outbox = OutboxWriter(ids, TestClock()),
            codec = UnusedCodec,
            uuidGenerator = ids,
            clock = TestClock(),
            dispatchers = dispatchers,
        )
    }

    private fun SqlDriver.countMissing(): Long =
        scalar("SELECT COUNT(*) FROM ledger_transaction WHERE search_text IS NULL") { it.getLong(0)!! }

    private fun SqlDriver.journalMode(): String = scalar("PRAGMA journal_mode") { it.getString(0).orEmpty() }

    private fun <T> SqlDriver.scalar(sql: String, read: (app.cash.sqldelight.db.SqlCursor) -> T): T =
        executeQuery(null, sql, { cursor -> cursor.next(); QueryResult.Value(read(cursor)) }, 0).value

    /** Benchmark chỉ đọc, không đi qua outbox, nên codec không bao giờ được gọi. */
    private object UnusedCodec : SyncPayloadCodec {
        override fun encode(payload: AccountPayload): JsonObject = error("not used")
        override fun encode(payload: CategoryPayload): JsonObject = error("not used")
        override fun encode(payload: TransactionPayload): JsonObject = error("not used")
        override fun decodeAccount(data: JsonObject): AccountPayload = error("not used")
        override fun decodeCategory(data: JsonObject): CategoryPayload = error("not used")
        override fun decodeTransaction(data: JsonObject): TransactionPayload = error("not used")
    }

    private companion object {
        const val TAG = "SearchBenchmark"
        const val FILE = "bench-search.db"
        const val KEY = "bench-only-key"
        const val COUNT = 50_000
        const val SEED = 42
        const val SEED_BATCH = 5_000
        const val PAGE = 50
        const val WARMUPS = 2
        const val RUNS = 7
        const val PULL_BATCHES = 20
        const val PULL_BATCH_SIZE = 500
        const val PARENTS = 10
        const val CHILDREN = 3
        const val RARE_QUERY = "kawasaki"
        val FIRST_DAY = LocalDate(2021, 1, 1)
        const val DAYS = 2_120 // 2021-01-01 → 2026-10-22

        fun category(parent: Int, child: Int? = null) = if (child == null) "cat-$parent" else "cat-$parent-$child"

        val PARENT_NAMES = listOf("Ăn uống", "Đi lại", "Mua sắm", "Hoá đơn", "Giải trí", "Sức khoẻ", "Giáo dục", "Gia đình", "Quà tặng", "Khác")
        val PAYEES = listOf(
            "Highlands Coffee", "Grab", "Circle K", "Bách Hoá Xanh", "Phúc Long", "Shopee", "Tiki", "Lazada", "Be",
            "Bún chả Hương Liên", "Cơm tấm Ba Ghiền", "Điện lực", "Viettel", "FPT", "CGV", "Guardian", "Pharmacity",
        )
        val NOTES = listOf(
            "Ăn trưa với đồng nghiệp", "Cà phê sáng", "Đi làm", "Mua đồ dùng", "Tiền điện tháng này", "Xem phim cuối tuần",
            "Quà sinh nhật", "Đổ xăng", "Ăn tối", "Mua thuốc", "", "Đi chợ", "Cước internet", "Học phí", "Ăn trưa",
        )
    }
}
