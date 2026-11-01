package com.uit.finance.feature.reports.presentation.overview

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.feature.reports.domain.model.ReportOverview
import com.uit.finance.feature.reports.domain.usecase.ObserveReportOverviewUseCase
import com.uit.finance.feature.reports.domain.usecase.ObserveTodayUseCase
import com.uit.finance.feature.reports.presentation.common.ShareAction
import com.uit.finance.feature.reports.presentation.common.ShareRowUi
import com.uit.finance.feature.reports.presentation.navigation.CategoryBreakdownDestination
import com.uit.finance.feature.reports.presentation.navigation.ReportTransactionsQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Kỳ đang xem = [Selection.period], hoặc `null` = "kỳ hiện tại": khi đó qua nửa đêm sang tháng mới thì màn tự sang tháng
 * mới (R8). Người dùng lùi về kỳ cũ rồi tới lại kỳ hiện tại thì lại về `null`.
 *
 * Hai nguồn "kỳ" tách bạch:
 * - đổi kỳ (‹ ›, Tháng/Năm) tính từ [selection], cập nhật đồng bộ theo từng intent: bấm ‹ hai lần thật nhanh lùi đúng hai kỳ;
 * - chạm vào dòng / cột dùng [displayed], tức đúng báo cáo đang hiện, nên dòng của kỳ cũ còn trên màn (chưa kịp tải kỳ
 *   mới) vẫn mở đúng kỳ, đúng tiền tệ của dòng đó.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
internal class ReportsViewModel(
    observeOverview: ObserveReportOverviewUseCase,
    observeToday: ObserveTodayUseCase,
    private val mapper: ReportsUiMapper,
) : MviViewModel<ReportsState, ReportsIntent, ReportsEffect>(ReportsState()) {

    private data class Selection(
        val mode: ReportMode = ReportMode.Month,
        val period: ReportPeriod? = null,
        val kind: CategoryKind = CategoryKind.EXPENSE,
        val currency: String? = null,
    )

    private val selection = MutableStateFlow(Selection())
    private val resumes = MutableStateFlow(0)
    private var today: LocalDate? = null

    /** Báo cáo đang hiện trên màn, cùng lần phát với [currentState]. */
    private var displayed: ReportOverview? = null

    init {
        combine(resumes.flatMapLatest { observeToday() }, selection) { day, chosen -> day to chosen }
            .flatMapLatest { (day, chosen) ->
                today = day
                observeOverview(chosen.periodFor(day), chosen.kind, chosen.currency, day)
            }
            .onEach { overview ->
                displayed = overview
                setState { mapper.map(overview, current = this) }
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ReportsIntent) = when (intent) {
        ReportsIntent.Previous -> moveTo { it.previous() }
        ReportsIntent.Next -> moveTo { it.next() }
        is ReportsIntent.SelectKind -> {
            selection.update { it.copy(kind = intent.kind) }
            setState { copy(kind = intent.kind, showOthers = false) }
        }
        is ReportsIntent.SelectMode -> selectMode(intent.mode)
        ReportsIntent.OpenCurrencyPicker -> setState { copy(showCurrencySheet = true) }
        ReportsIntent.DismissCurrencyPicker -> setState { copy(showCurrencySheet = false) }
        is ReportsIntent.SelectCurrency -> {
            selection.update { it.copy(currency = intent.currency) }
            setState { copy(showCurrencySheet = false, showOthers = false) }
        }
        is ReportsIntent.SelectTrendBar -> selectTrendBar(intent.index)
        is ReportsIntent.OpenRow -> openRow(intent.row)
        ReportsIntent.AddTransaction -> sendEffect(ReportsEffect.AddTransaction)
        ReportsIntent.Back -> sendEffect(ReportsEffect.NavigateBack)
        ReportsIntent.Resumed -> resumes.value += 1
    }

    private fun moveTo(step: (ReportPeriod) -> ReportPeriod) {
        val day = today ?: return
        val from = selection.value.periodFor(day)
        val target = step(from)
        if (target.isAfter(day)) return
        select(target, day)
    }

    /** Năm → Tháng: tháng hiện tại nếu là năm nay, không thì tháng 12 của năm đó. Tháng → Năm: năm của tháng đó. */
    private fun selectMode(mode: ReportMode) {
        val day = today ?: return
        val from = selection.value.periodFor(day)
        val target = when (mode) {
            ReportMode.Month -> when (from) {
                is ReportPeriod.Month -> from
                is ReportPeriod.Year -> if (from.year == day.year) ReportPeriod.monthOf(day) else ReportPeriod.Month(YearMonth(from.year, 12))
            }
            ReportMode.Year -> when (from) {
                is ReportPeriod.Month -> ReportPeriod.Year(from.month.year)
                is ReportPeriod.Year -> from
            }
        }
        selection.update { it.copy(mode = mode) }
        select(target, day)
    }

    /** Chạm cột tháng: sang tháng đó (chế độ Năm thì chuyển về xem tháng). Tháng tương lai thì bỏ qua. */
    private fun selectTrendBar(index: Int) {
        val day = today ?: return
        val overview = displayed ?: return
        val point = overview.trend.getOrNull(index) ?: return
        if (point.isFuture) return
        val month = point.month
        selection.update { it.copy(mode = ReportMode.Month) }
        select(ReportPeriod.Month(month), day)
    }

    private fun select(period: ReportPeriod, day: LocalDate) {
        val isCurrent = period == current(if (period is ReportPeriod.Year) ReportMode.Year else ReportMode.Month, day)
        selection.update { it.copy(period = if (isCurrent) null else period) }
        setState { copy(showOthers = false) }
    }

    private fun openRow(row: ShareRowUi) {
        val overview = displayed ?: return
        val period = overview.period
        when (row.action) {
            ShareAction.None -> Unit
            ShareAction.ToggleOthers -> setState { copy(showOthers = !showOthers) }
            ShareAction.OpenBreakdown -> row.categoryId?.let { id ->
                sendEffect(ReportsEffect.OpenBreakdown(CategoryBreakdownDestination.of(id, period, overview.kind, overview.currency)))
            }
            ShareAction.OpenTransactions -> sendEffect(
                ReportsEffect.OpenTransactions(
                    ReportTransactionsQuery(period.start, period.end, row.categoryId, overview.kind, overview.accountIds),
                ),
            )
        }
    }

    private fun Selection.periodFor(day: LocalDate): ReportPeriod = period?.takeUnless { it.isAfter(day) } ?: current(mode, day)

    private fun current(mode: ReportMode, day: LocalDate): ReportPeriod = when (mode) {
        ReportMode.Month -> ReportPeriod.monthOf(day)
        ReportMode.Year -> ReportPeriod.yearOf(day)
    }
}
