@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.uit.finance.feature.transactions.presentation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeSyncKit
import com.uit.finance.core.ledger.testing.FakeTransactionRepository
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.ledger.testing.transaction
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.ui.transaction.TransactionItemFormatter
import com.uit.finance.feature.transactions.domain.model.DatePreset
import com.uit.finance.feature.transactions.domain.model.HistoryFilter
import com.uit.finance.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTodayUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionFormDataUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionHistoryUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionTotalsUseCase
import com.uit.finance.feature.transactions.presentation.history.CurrencyTotalsUi
import com.uit.finance.feature.transactions.presentation.history.DateBound
import com.uit.finance.feature.transactions.presentation.history.FilterChipKey
import com.uit.finance.feature.transactions.presentation.history.FilterChipUi
import com.uit.finance.feature.transactions.presentation.history.HistoryArgs
import com.uit.finance.feature.transactions.presentation.history.HistoryEffect
import com.uit.finance.feature.transactions.presentation.history.HistoryIntent
import com.uit.finance.feature.transactions.presentation.history.HistoryViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = FakeTransactionRepository(accounts, categories)
    private val sync = FakeSyncKit()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Đồng hồ chạy theo thời gian ảo của test: advanceTimeBy vừa đẩy delay vừa đẩy giờ. */
    private fun clockAt(start: String) = object : Clock {
        override fun now(): Instant = Instant.parse(start) + dispatcher.scheduler.currentTime.milliseconds
    }

    private val created = mutableListOf<HistoryViewModel>()

    private fun viewModel(args: HistoryArgs = HistoryArgs(), start: String = NOON_14_NOV) = HistoryViewModel(
        args = args,
        observeHistory = ObserveTransactionHistoryUseCase(transactions, accounts, categories),
        observeTotals = ObserveTransactionTotalsUseCase(transactions),
        observeFormData = ObserveTransactionFormDataUseCase(accounts, categories),
        observeToday = ObserveTodayUseCase(clockAt(start), TimeZone.UTC),
        observeSync = sync.observeOverview,
        deleteTransaction = DeleteTransactionUseCase(transactions),
        syncNow = sync.syncNow,
        acknowledgeSyncIssues = sync.acknowledgeIssues,
        formatter = TransactionItemFormatter(MoneyFormatter()),
        money = MoneyFormatter(),
    ).also { created += it }

    /**
     * ViewModel giữ luồng "hôm nay" chạy mãi (mỗi nửa đêm một lần). Huỷ scope của nó ngay trong thân test, trước khi
     * runTest dọn dẹp; không thì runTest cứ đẩy thời gian ảo qua từng ngày và không bao giờ xong.
     */
    private fun runVmTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
        }
    }

    /** Bộ lọc của lần gọi repository gần nhất: đúng thứ danh sách đang hiện. */
    private val applied: TransactionFilter get() = transactions.requestedFilters.last().first

    private fun HistoryViewModel.ids() = state.value.sections.flatMap { s -> s.items.map { it.id } }

    private fun seed(count: Int) {
        accounts.accounts.value = listOf(account("a", "Ví"))
        transactions.transactions.value = List(count) { index ->
            transaction("t$index", TransactionType.EXPENSE, "a", 1_000L + index, LocalDate(2023, 11, 14 - index % 2))
        }
    }

    // ---- Lịch sử (giữ nguyên hành vi cũ) ----

    @Test
    fun `gom theo ngày với nhãn tương đối`() = runVmTest {
        seed(3)
        val vm = viewModel()
        runCurrent()

        val sections = vm.state.value.sections
        assertEquals(listOf("Hôm nay", "Hôm qua"), sections.map { it.label })
        assertEquals(listOf("t0", "t2"), sections[0].items.map { it.id })
        assertEquals(false, vm.state.value.canLoadMore)
        assertFalse(vm.state.value.isFiltered)
        assertNull(vm.state.value.totals)
    }

    @Test
    fun `chạm gần cuối thì nới cửa sổ thêm một trang`() = runVmTest {
        seed(50)
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.state.value.canLoadMore)

        vm.onIntent(HistoryIntent.LoadMore)
        runCurrent()

        assertEquals(listOf(50, 100), transactions.requestedFilters.map { it.second })
        assertEquals(false, vm.state.value.canLoadMore)
    }

    @Test
    fun `vuốt xoá ẩn ngay - hoàn tác thì hiện lại và không xoá thật`() = runVmTest {
        seed(2)
        val vm = viewModel()
        runCurrent()

        vm.onIntent(HistoryIntent.DeleteSwiped("t0"))
        assertEquals(listOf("t1"), vm.ids())

        vm.onIntent(HistoryIntent.UndoDelete)
        runCurrent()
        assertEquals(listOf("t0", "t1"), vm.ids())
        assertEquals(emptyList(), transactions.deletedIds)
    }

    @Test
    fun `hết giờ hoàn tác hoặc vuốt dòng khác thì xoá thật dòng trước`() = runVmTest {
        seed(3)
        val vm = viewModel()
        runCurrent()

        vm.onIntent(HistoryIntent.DeleteSwiped("t0"))
        vm.onIntent(HistoryIntent.DeleteSwiped("t1"))
        runCurrent()
        assertEquals(listOf("t0"), transactions.deletedIds)
        assertEquals("t1", vm.state.value.pendingDeleteId)

        vm.onIntent(HistoryIntent.CommitPendingDelete)
        runCurrent()
        assertEquals(listOf("t0", "t1"), transactions.deletedIds)
        assertEquals(listOf("t2"), vm.ids())
    }

    @Test
    fun `chạm dòng mở form sửa và nút thêm mở form mới`() = runVmTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(HistoryIntent.ItemClicked("t9"))
            assertEquals(HistoryEffect.OpenEditor("t9"), awaitItem())
            vm.onIntent(HistoryIntent.AddClicked)
            assertEquals(HistoryEffect.OpenEditor(null), awaitItem())
        }
    }

    @Test
    fun `kéo để làm mới chạy một vòng sync`() = runVmTest {
        val vm = viewModel()
        vm.onIntent(HistoryIntent.SyncNow)
        runCurrent()
        assertEquals(1, sync.engine.syncCalls)
        assertEquals(false, vm.state.value.isRefreshing)
    }

    // ---- Từ khoá ----

    @Test
    fun `ô tìm hiện ngay chữ đang gõ nhưng chỉ tìm sau 300 ms ngừng gõ`() = runVmTest {
        val vm = viewModel()
        runCurrent()

        vm.onIntent(HistoryIntent.QueryChanged("ca"))
        advanceTimeBy(200.milliseconds)
        vm.onIntent(HistoryIntent.QueryChanged("ca phe"))
        runCurrent()
        assertEquals("ca phe", vm.state.value.query)

        advanceTimeBy(299.milliseconds)
        runCurrent()
        assertNull(applied.query, "chưa đủ 300 ms kể từ lần gõ cuối")

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals("ca phe", applied.query)
        assertEquals(listOf(null, "ca phe"), transactions.requestedFilters.map { it.first.query }, "không tìm chữ dở dang")
    }

    @Test
    fun `xoá hết chữ thì về danh sách đầy đủ ngay không chờ`() = runVmTest {
        val vm = viewModel()
        vm.onIntent(HistoryIntent.QueryChanged("grab"))
        advanceTimeBy(300.milliseconds)
        runCurrent()
        assertEquals("grab", applied.query)

        vm.onIntent(HistoryIntent.QueryChanged(""))
        runCurrent()

        assertEquals(TransactionFilter.ALL, applied)
        assertFalse(vm.state.value.isFiltered)
    }

    @Test
    fun `đổi từ khoá thì quay về trang đầu`() = runVmTest {
        seed(50)
        val vm = viewModel()
        runCurrent()
        vm.onIntent(HistoryIntent.LoadMore)
        runCurrent()

        vm.onIntent(HistoryIntent.QueryChanged("ví"))
        advanceTimeBy(300.milliseconds)
        runCurrent()

        assertEquals(50, transactions.requestedFilters.last().second)
    }

    // ---- Chip + Xoá lọc ----

    @Test
    fun `mỗi điều kiện là một chip - số trên nút lọc bằng số chip`() = runVmTest {
        accounts.accounts.value = listOf(account("a", "Ví"), account("b", "Bank"))
        categories.categories.value = listOf(category("food", "Ăn uống"))
        val vm = viewModel()
        runCurrent()

        vm.onIntent(HistoryIntent.DatePresetSelected(DatePreset.THIS_MONTH))
        vm.onIntent(HistoryIntent.TypeToggled(TransactionType.INCOME))
        vm.onIntent(HistoryIntent.TypeToggled(TransactionType.EXPENSE))
        vm.onIntent(HistoryIntent.AccountToggled("a"))
        vm.onIntent(HistoryIntent.CategoryToggled("food"))
        vm.onIntent(HistoryIntent.DraftsOnlyChanged(true))
        runCurrent()

        assertEquals(
            listOf(
                FilterChipUi(FilterChipKey.Date, "Tháng này"),
                FilterChipUi(FilterChipKey.Types, "Chi, Thu"),
                FilterChipUi(FilterChipKey.Accounts, "Ví"),
                FilterChipUi(FilterChipKey.Categories, "Ăn uống"),
                FilterChipUi(FilterChipKey.Status, "Nháp"),
            ),
            vm.state.value.chips,
        )
        assertEquals(5, vm.state.value.filter.activeCount)

        vm.onIntent(HistoryIntent.AccountToggled("b"))
        assertEquals("2 ví", vm.state.value.chips.first { it.key == FilterChipKey.Accounts }.label)
    }

    @Test
    fun `gỡ từng chip chỉ bỏ đúng điều kiện đó`() = runVmTest {
        val vm = viewModel()
        runCurrent()
        vm.onIntent(HistoryIntent.DatePresetSelected(DatePreset.LAST_30_DAYS))
        vm.onIntent(HistoryIntent.TypeToggled(TransactionType.TRANSFER))
        vm.onIntent(HistoryIntent.AccountToggled("a"))
        vm.onIntent(HistoryIntent.DraftsOnlyChanged(true))
        runCurrent()

        vm.onIntent(HistoryIntent.ChipRemoved(FilterChipKey.Types))
        runCurrent()
        assertNull(applied.types, "đã chuẩn hoá: tập rỗng là không lọc")
        assertEquals(setOf("a"), applied.accountIds)
        assertEquals(TransactionStatus.DRAFT, applied.status)
        assertEquals(LocalDate(2023, 10, 16) to LocalDate(2023, 11, 14), applied.from to applied.to)

        vm.onIntent(HistoryIntent.ChipRemoved(FilterChipKey.Date))
        vm.onIntent(HistoryIntent.ChipRemoved(FilterChipKey.Accounts))
        vm.onIntent(HistoryIntent.ChipRemoved(FilterChipKey.Status))
        runCurrent()
        assertEquals(TransactionFilter.ALL, applied)
        assertEquals(emptyList(), vm.state.value.chips)
    }

    @Test
    fun `Xoá lọc bỏ cả từ khoá lẫn mọi điều kiện`() = runVmTest {
        seed(2)
        val vm = viewModel()
        vm.onIntent(HistoryIntent.QueryChanged("không có"))
        vm.onIntent(HistoryIntent.TypeToggled(TransactionType.INCOME))
        advanceTimeBy(300.milliseconds)
        runCurrent()
        assertTrue(vm.state.value.isFiltered)
        assertEquals(emptyList(), vm.state.value.sections, "lọc ra rỗng: màn hình hiện Không tìm thấy giao dịch nào")

        vm.onIntent(HistoryIntent.ClearFilters)
        runCurrent()

        assertEquals(TransactionFilter.ALL, applied)
        assertEquals("", vm.state.value.query)
        assertEquals(HistoryFilter(), vm.state.value.filter)
        assertFalse(vm.state.value.isFiltered)
        assertEquals(listOf("t0", "t1"), vm.ids())
    }

    // ---- Dòng tổng ----

    @Test
    fun `đang lọc thì có dòng tổng - đếm cả nháp nhưng không cộng nháp`() = runVmTest {
        accounts.accounts.value = listOf(account("a", "Ví"), account("u", "Ví đô", currency = "USD"))
        transactions.transactions.value = listOf(
            transaction("t1", TransactionType.EXPENSE, "a", 45_000, LocalDate(2023, 11, 10)),
            transaction("t2", TransactionType.EXPENSE, "a", 60_000, LocalDate(2023, 11, 11)).copy(status = TransactionStatus.DRAFT),
            transaction("t3", TransactionType.INCOME, "a", 1_000_000, LocalDate(2023, 11, 12)),
            transaction("t4", TransactionType.EXPENSE, "u", 550, LocalDate(2023, 11, 13), currency = "USD"),
        )
        val vm = viewModel()

        vm.onIntent(HistoryIntent.DatePresetSelected(DatePreset.THIS_MONTH))
        runCurrent()

        val totals = vm.state.value.totals!!
        assertEquals("4 giao dịch (gồm 1 nháp, không tính vào tổng)", totals.summary)
        assertEquals(
            listOf(CurrencyTotalsUi("USD", income = null, expense = "5,50"), CurrencyTotalsUi("VND", income = "1.000.000", expense = "45.000")),
            totals.currencies,
        )
    }

    // ---- "Hôm nay" + khoảng ngày ----

    @Test
    fun `preset Tháng này tự sang tháng mới khi qua nửa đêm cuối tháng`() = runVmTest {
        val vm = viewModel(start = "2026-10-31T23:59:00Z")
        vm.onIntent(HistoryIntent.DatePresetSelected(DatePreset.THIS_MONTH))
        runCurrent()
        assertEquals(LocalDate(2026, 10, 1) to LocalDate(2026, 10, 31), applied.from to applied.to)

        advanceTimeBy(60.seconds)
        runCurrent()

        assertEquals(LocalDate(2026, 11, 1) to LocalDate(2026, 11, 30), applied.from to applied.to)
        assertEquals("Tháng này", vm.state.value.chips.single().label)
    }

    @Test
    fun `khoảng ngày tuỳ chọn - từ ngày không chọn được sau đến ngày`() = runVmTest {
        val vm = viewModel()
        runCurrent()
        vm.onIntent(HistoryIntent.DatePresetSelected(DatePreset.CUSTOM))
        assertEquals(emptyList(), vm.state.value.chips, "Tuỳ chọn chưa có ngày nào thì chưa phải điều kiện")

        vm.onIntent(HistoryIntent.DatePickerOpened(DateBound.To))
        vm.onIntent(HistoryIntent.CustomDateSelected(DateBound.To, LocalDate(2023, 11, 5)))
        vm.onIntent(HistoryIntent.DatePickerOpened(DateBound.From))
        val picker = vm.state.value.datePicker!!
        assertEquals(LocalDate(2023, 11, 5), picker.range.endInclusive)
        assertEquals(LocalDate(2023, 11, 5), picker.selected, "hôm nay 14/11 nằm ngoài khoảng nên kẹp về 05/11")

        vm.onIntent(HistoryIntent.CustomDateSelected(DateBound.From, LocalDate(2023, 11, 1)))
        runCurrent()
        assertNull(vm.state.value.datePicker)
        assertEquals(LocalDate(2023, 11, 1) to LocalDate(2023, 11, 5), applied.from to applied.to)
        assertEquals("01/11/2023 – 05/11/2023", vm.state.value.chips.single().label)
    }

    // ---- Mở từ màn khác (F11) ----

    @Test
    fun `mở từ màn khác thì bộ lọc đã điền sẵn và có nút quay lại`() = runVmTest {
        categories.categories.value = listOf(category("food", "Ăn uống"))
        val args = HistoryArgs(
            initialFilter = HistoryFilter.prefilled(
                from = LocalDate(2023, 10, 1),
                to = LocalDate(2023, 10, 31),
                categoryId = "food",
                type = TransactionType.EXPENSE,
            ),
            showBack = true,
        )
        val vm = viewModel(args)
        runCurrent()

        assertEquals(
            TransactionFilter(
                from = LocalDate(2023, 10, 1),
                to = LocalDate(2023, 10, 31),
                types = setOf(TransactionType.EXPENSE),
                categoryIds = setOf("food"),
            ),
            transactions.requestedFilters.single().first,
        )
        assertTrue(vm.state.value.showBack)
        assertEquals(listOf("01/10/2023 – 31/10/2023", "Chi", "Ăn uống"), vm.state.value.chips.map { it.label })
        vm.effects.test {
            vm.onIntent(HistoryIntent.BackClicked)
            assertEquals(HistoryEffect.Close, awaitItem())
        }
    }

    private companion object {
        const val NOON_14_NOV = "2023-11-14T12:00:00Z"
    }
}
