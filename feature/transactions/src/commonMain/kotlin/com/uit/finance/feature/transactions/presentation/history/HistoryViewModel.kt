package com.uit.finance.feature.transactions.presentation.history

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.onFailure
import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionTotals
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.toRelativeLabel
import com.uit.finance.core.sync.domain.usecase.AcknowledgeSyncIssuesUseCase
import com.uit.finance.core.sync.domain.usecase.ObserveSyncOverviewUseCase
import com.uit.finance.core.sync.domain.usecase.SyncNowUseCase
import com.uit.finance.core.ui.ledger.toLedgerUiText
import com.uit.finance.core.ui.sync.toSyncMessage
import com.uit.finance.core.ui.transaction.TransactionItemFormatter
import com.uit.finance.feature.transactions.domain.model.DatePreset
import com.uit.finance.feature.transactions.domain.model.HistoryFilter
import com.uit.finance.feature.transactions.domain.model.TransactionDetails
import com.uit.finance.feature.transactions.domain.model.TransactionFormData
import com.uit.finance.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTodayUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionFormDataUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionHistoryUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionTotalsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Lịch sử giao dịch + tìm kiếm và lọc (docs/SEARCH-PLAN.md). Cuộn vô hạn = nới [limit] thêm [PAGE_SIZE] mỗi lần chạm
 * cuối danh sách; đổi từ khoá hay điều kiện thì quay về trang đầu.
 *
 * - Từ khoá: ô tìm hiện ngay chữ đang gõ, danh sách chỉ tìm sau [QUERY_DEBOUNCE_MS] ngừng gõ. Xoá hết chữ thì về
 *   ngay, không chờ.
 * - "Hôm nay" là luồng tự cập nhật (G12), đăng ký lại mỗi lần màn resume: preset "Tháng này" và nhãn "Hôm nay" tự
 *   đổi khi qua nửa đêm.
 * - Dòng tổng chỉ tính khi đang lọc; không lọc thì không chạy câu tổng trên cả bảng.
 *
 * Vuốt xoá có hoàn tác bằng **xoá trễ**: dòng bị ẩn ngay, chỉ gọi xoá thật khi hết thời gian hoàn tác, khi vuốt xoá
 * dòng khác, hoặc khi rời màn hình. Không xoá rồi ghi lại để hoàn tác, vì server cho xoá luôn thắng (S4): bản ghi
 * lại sẽ bị `CONFLICT` và biến mất ở lần sync sau.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class) // flatMapLatest, debounce
internal class HistoryViewModel(
    args: HistoryArgs,
    observeHistory: ObserveTransactionHistoryUseCase,
    observeTotals: ObserveTransactionTotalsUseCase,
    observeFormData: ObserveTransactionFormDataUseCase,
    observeToday: ObserveTodayUseCase,
    observeSync: ObserveSyncOverviewUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val syncNow: SyncNowUseCase,
    private val acknowledgeSyncIssues: AcknowledgeSyncIssuesUseCase,
    private val formatter: TransactionItemFormatter,
    private val money: MoneyFormatter,
) : MviViewModel<HistoryState, HistoryIntent, HistoryEffect>(
    HistoryState(showBack = args.showBack, filter = args.initialFilter),
) {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(args.initialFilter)
    private val limit = MutableStateFlow(PAGE_SIZE)
    private val resumes = MutableStateFlow(0)

    private var latest: List<TransactionDetails> = emptyList()
    private var today: LocalDate? = null
    private var formData = TransactionFormData(accounts = emptyList(), categories = emptyList())

    /** Dòng đã gọi xoá nhưng Flow chưa phát bản mới: tiếp tục ẩn để không nháy lại. */
    private val hiddenUntilGone = mutableSetOf<String>()

    private class Page(
        val today: LocalDate,
        val filter: TransactionFilter,
        val details: List<TransactionDetails>,
        val totals: TransactionTotals?,
    )

    init {
        val typedQuery = query.debounce { if (it.isBlank()) 0L else QUERY_DEBOUNCE_MS }
        val day = resumes.flatMapLatest { observeToday() }
        combine(day, filter, typedQuery) { today, conditions, typed -> today to conditions.toTransactionFilter(typed, today) }
            .distinctUntilChanged()
            .flatMapLatest { (today, applied) ->
                limit.value = PAGE_SIZE // điều kiện mới: bắt đầu lại từ trang đầu
                val totals = if (applied == TransactionFilter.ALL) flowOf(null) else observeTotals(applied)
                combine(limit.flatMapLatest { observeHistory(applied, it) }, totals) { details, sums ->
                    Page(today, applied, details, sums)
                }
            }
            .onEach(::show)
            .launchIn(viewModelScope)
        observeFormData()
            .onEach { data ->
                formData = data
                setState {
                    copy(
                        accountOptions = data.accounts.toAccountFilterOptions(),
                        categoryOptions = data.categories.toCategoryFilterOptions(),
                    ).withChips()
                }
            }
            .launchIn(viewModelScope)
        observeSync()
            .onEach { overview -> setState { copy(sync = overview) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: HistoryIntent) = when (intent) {
        HistoryIntent.LoadMore -> loadMore()
        HistoryIntent.AddClicked -> sendEffect(HistoryEffect.OpenEditor(null))
        is HistoryIntent.ItemClicked -> sendEffect(HistoryEffect.OpenEditor(intent.transactionId))
        HistoryIntent.CategoriesClicked -> sendEffect(HistoryEffect.OpenCategories)
        is HistoryIntent.DeleteSwiped -> deleteLater(intent.transactionId)
        HistoryIntent.UndoDelete -> setState { copy(pendingDeleteId = null).rebuild() }
        HistoryIntent.CommitPendingDelete -> commitPendingDelete()
        HistoryIntent.SyncNow -> startSync()
        HistoryIntent.AcknowledgeSyncIssues -> {
            viewModelScope.launch { acknowledgeSyncIssues() }
            Unit
        }
        HistoryIntent.MessageShown -> setState { copy(message = null) }
        HistoryIntent.Refresh -> resumes.value += 1
        HistoryIntent.BackClicked -> sendEffect(HistoryEffect.Close)
        is HistoryIntent.QueryChanged -> {
            query.value = intent.value
            setState { copy(query = intent.value) }
        }
        HistoryIntent.FilterClicked -> setState { copy(isFilterSheetOpen = true) }
        HistoryIntent.FilterSheetDismissed -> setState { copy(isFilterSheetOpen = false, datePicker = null) }
        is HistoryIntent.DatePresetSelected -> updateFilter {
            if (intent.preset == DatePreset.CUSTOM) copy(datePreset = DatePreset.CUSTOM)
            else copy(datePreset = intent.preset, customFrom = null, customTo = null)
        }
        is HistoryIntent.DatePickerOpened -> openDatePicker(intent.bound)
        HistoryIntent.DatePickerDismissed -> setState { copy(datePicker = null) }
        is HistoryIntent.CustomDateSelected -> {
            setState { copy(datePicker = null) }
            updateFilter {
                when (intent.bound) {
                    DateBound.From -> copy(datePreset = DatePreset.CUSTOM, customFrom = intent.date)
                    DateBound.To -> copy(datePreset = DatePreset.CUSTOM, customTo = intent.date)
                }
            }
        }
        is HistoryIntent.TypeToggled -> updateFilter { copy(types = types.toggle(intent.type)) }
        is HistoryIntent.AccountToggled -> updateFilter { copy(accountIds = accountIds.toggle(intent.accountId)) }
        is HistoryIntent.CategoryToggled -> updateFilter { copy(categoryIds = categoryIds.toggle(intent.categoryId)) }
        is HistoryIntent.DraftsOnlyChanged -> updateFilter { copy(draftsOnly = intent.draftsOnly) }
        is HistoryIntent.ChipRemoved -> updateFilter { without(intent.key) }
        HistoryIntent.ClearFilters -> {
            query.value = ""
            setState { copy(query = "") }
            updateFilter { HistoryFilter() }
        }
    }

    private fun show(page: Page) {
        latest = page.details
        today = page.today
        setState {
            copy(isFiltered = page.filter != TransactionFilter.ALL, totals = page.totals?.toUi(money))
                .rebuild(canLoadMore = page.details.size >= limit.value)
        }
    }

    private fun updateFilter(change: HistoryFilter.() -> HistoryFilter) {
        val updated = filter.value.change()
        filter.value = updated
        setState { copy(filter = updated).withChips() }
    }

    /** Ngày chọn được giống form giao dịch; "từ" không sau "đến" và ngược lại. */
    private fun openDatePicker(bound: DateBound) {
        val now = today ?: return
        val conditions = filter.value
        val earliest = LedgerLimits.EARLIEST_DATE
        val latestDate = now.plus(LedgerLimits.MAX_AHEAD)
        val range = when (bound) {
            DateBound.From -> earliest..(conditions.customTo ?: latestDate)
            DateBound.To -> (conditions.customFrom ?: earliest)..latestDate
        }
        val current = when (bound) {
            DateBound.From -> conditions.customFrom
            DateBound.To -> conditions.customTo
        }
        val selected = (current ?: now).coerceIn(range.start, range.endInclusive)
        setState { copy(datePicker = DatePickerUi(bound, selected, range)) }
    }

    private fun loadMore() {
        if (currentState.canLoadMore) limit.value += PAGE_SIZE
    }

    private fun deleteLater(transactionId: String) {
        commitPendingDelete() // mỗi lúc chỉ một dòng chờ hoàn tác
        setState { copy(pendingDeleteId = transactionId).rebuild() }
    }

    private fun commitPendingDelete() {
        val id = currentState.pendingDeleteId ?: return
        setState { copy(pendingDeleteId = null) } // vẫn ẩn: Flow sẽ bỏ dòng này khi xoá xong
        hiddenUntilGone += id
        setState { rebuild() }
        viewModelScope.launch {
            deleteTransaction(id).onFailure { error ->
                hiddenUntilGone -= id
                setState { copy(message = error.toLedgerUiText()).rebuild() }
            }
        }
    }

    private fun startSync() {
        if (currentState.isRefreshing) return
        setState { copy(isRefreshing = true) }
        viewModelScope.launch {
            syncNow().onFailure { error -> setState { copy(message = error.toSyncMessage()) } }
            setState { copy(isRefreshing = false) }
        }
    }

    private fun HistoryState.withChips(): HistoryState = copy(chips = filter.chips(formData.accounts, formData.categories))

    private fun HistoryState.rebuild(canLoadMore: Boolean = this.canLoadMore): HistoryState {
        val now = today ?: return this
        hiddenUntilGone.retainAll(latest.map { it.transaction.id }.toSet())
        val hidden = hiddenUntilGone + listOfNotNull(pendingDeleteId)
        val sections = latest
            .filterNot { it.transaction.id in hidden }
            .groupBy { it.transaction.occurredOn }
            .map { (date, items) ->
                DaySection(
                    key = date.toString(),
                    label = date.toRelativeLabel(now),
                    items = items.map { formatter.format(it.transaction, it.account, it.counterAccount, it.category, today = null) },
                )
            }
        return copy(isLoading = false, sections = sections, canLoadMore = canLoadMore)
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    companion object {
        const val PAGE_SIZE = 50
        const val QUERY_DEBOUNCE_MS = 300L
    }
}
