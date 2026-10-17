package com.uit.finance.feature.budgets.presentation.list

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.onFailure
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.sync.domain.usecase.AcknowledgeSyncIssuesUseCase
import com.uit.finance.core.sync.domain.usecase.ObserveSyncOverviewUseCase
import com.uit.finance.core.sync.domain.usecase.SyncNowUseCase
import com.uit.finance.core.ui.sync.toSyncMessage
import com.uit.finance.feature.budgets.domain.model.BudgetOverview
import com.uit.finance.feature.budgets.domain.model.BudgetProgress
import com.uit.finance.feature.budgets.domain.model.BudgetTotal
import com.uit.finance.feature.budgets.domain.usecase.ObserveBudgetProgressUseCase
import com.uit.finance.feature.budgets.domain.usecase.ObserveTodayUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.number
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth

/**
 * Màn "Ngân sách" của một tháng (ADR-007 §1).
 *
 * "Hôm nay" là một luồng tự cập nhật (G12): đăng ký lại mỗi khi app resume, nên màn để mở qua nửa đêm hay qua
 * tháng mới đều tự tính lại. Khi đang xem tháng hiện tại ([selectedMonth] = `null`), qua tháng mới thì màn tự
 * chuyển sang tháng mới; đang xem tháng cũ thì giữ nguyên.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
internal class BudgetListViewModel(
    observeProgress: ObserveBudgetProgressUseCase,
    observeToday: ObserveTodayUseCase,
    observeSync: ObserveSyncOverviewUseCase,
    private val syncNow: SyncNowUseCase,
    private val acknowledgeSyncIssues: AcknowledgeSyncIssuesUseCase,
    private val money: MoneyFormatter,
) : MviViewModel<BudgetListState, BudgetListIntent, BudgetListEffect>(BudgetListState()) {

    private val resumes = MutableStateFlow(0)

    /** `null` = đi theo tháng hiện tại. */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)
    private var currentMonth: YearMonth? = null
    private var shownMonth: YearMonth? = null

    init {
        val today = resumes.flatMapLatest { observeToday() }
        combine(today, selectedMonth) { day, selected -> day to (selected ?: day.yearMonth) }
            .flatMapLatest { (day, month) -> observeProgress(month, day).map { overview -> day to overview } }
            .onEach { (day, overview) -> setState { withOverview(day, overview) } }
            .launchIn(viewModelScope)
        observeSync()
            .onEach { overview -> setState { copy(sync = overview) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: BudgetListIntent) = when (intent) {
        BudgetListIntent.PreviousMonth -> shownMonth?.let { selectedMonth.value = it.minusMonth() } ?: Unit
        BudgetListIntent.NextMonth -> nextMonth()
        BudgetListIntent.AddClicked -> sendEffect(BudgetListEffect.OpenEditor(null))
        is BudgetListIntent.ItemClicked -> sendEffect(BudgetListEffect.OpenEditor(intent.budgetId))
        BudgetListIntent.SyncNow -> startSync()
        BudgetListIntent.AcknowledgeSyncIssues -> {
            viewModelScope.launch { acknowledgeSyncIssues() }
            Unit
        }
        BudgetListIntent.MessageShown -> setState { copy(message = null) }
        BudgetListIntent.Resumed -> resumes.value += 1
    }

    private fun nextMonth() {
        val shown = shownMonth ?: return
        val current = currentMonth ?: return
        if (shown >= current) return
        val next = shown.plusMonth()
        selectedMonth.value = if (next >= current) null else next
    }

    private fun startSync() {
        if (currentState.isRefreshing) return
        setState { copy(isRefreshing = true) }
        viewModelScope.launch {
            syncNow().onFailure { error -> setState { copy(message = error.toSyncMessage()) } }
            setState { copy(isRefreshing = false) }
        }
    }

    private fun BudgetListState.withOverview(today: LocalDate, overview: BudgetOverview): BudgetListState {
        currentMonth = today.yearMonth
        shownMonth = overview.month
        return copy(
            isLoading = false,
            monthLabel = "Tháng ${overview.month.month.number}/${overview.month.year}",
            isCurrentMonth = overview.month == today.yearMonth,
            canGoNext = overview.month < today.yearMonth,
            totals = overview.totals.map { it.toUi() },
            items = overview.items.map { it.toUi() },
        )
    }

    private fun BudgetProgress.toUi() = BudgetItemUi(
        id = budget.id,
        categoryName = category?.name,
        categoryIcon = category?.icon,
        categoryColor = category?.color,
        categoryArchived = category?.archived == true,
        budgeted = budget.amount.toUi(),
        spent = spent.toUi(),
        remaining = remaining.toUi(),
        percentLabel = "$percent%",
        progress = ratio.coerceIn(0.0, 1.0).toFloat(),
        tone = tone,
        dailyAllowance = dailyAllowance?.toUi(),
    )

    private fun BudgetTotal.toUi() = BudgetTotalUi(budgeted.toUi(), spent.toUi(), remaining.toUi())

    private fun Money.toUi(): MoneyUi = money.format(this).let { MoneyUi(it.currency, it.amount, it.isNegative) }
}
