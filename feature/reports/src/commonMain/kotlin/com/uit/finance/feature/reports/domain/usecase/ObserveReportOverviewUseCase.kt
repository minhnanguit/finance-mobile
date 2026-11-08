package com.uit.finance.feature.reports.domain.usecase

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.CategoryTotal
import com.uit.finance.core.ledger.domain.model.MonthlyTotal
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.BalanceRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.SpendingRepository
import com.uit.finance.feature.reports.domain.model.ReportComparison
import com.uit.finance.feature.reports.domain.model.ReportOverview
import com.uit.finance.feature.reports.domain.model.TrendPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/**
 * Mọi số của màn Báo cáo (docs/REPORTS-PLAN.md mục 1, R1–R9) cho [period], loại [kind], tiền tệ [currency]
 * (`null` = mặc định, R2). Tự cập nhật khi ghi hay sync về giao dịch.
 *
 * - Thu/chi của kỳ lấy từ đúng nguồn của Trang chủ (`observeCashFlow`), nên hai màn luôn bằng nhau.
 * - So sánh (R6): kỳ đang chạy thì so cùng số ngày với kỳ trước; kỳ trước = 0 thì không so.
 * - Xu hướng: 6 tháng kết thúc ở tháng đang xem, hoặc 12 tháng của năm đang xem. Tháng trống điền 0.
 *
 * [period] ở tương lai so với [today] thì dùng kỳ hiện tại (R1).
 */
class ObserveReportOverviewUseCase(
    private val spending: SpendingRepository,
    private val balances: BalanceRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {
    operator fun invoke(period: ReportPeriod, kind: CategoryKind, currency: String?, today: LocalDate): Flow<ReportOverview> {
        val shown = if (period.isAfter(today)) period.currentFor(today) else period
        val compare = comparisonRanges(shown, today)
        val trendMonths = trendMonths(shown)

        // R9: sync đổ về nhiều trang liên tiếp thì mỗi nguồn chỉ giữ bản mới nhất, combine tính lại một lần.
        val sources = combine(
            balances.observeCashFlow(shown.start, shown.end).conflate(),
            spending.observeCategoryTotals(shown.start, shown.end, kind, groupByParent = true).conflate(),
            categories.observeCategories().conflate(),
            accounts.observeAccounts().conflate(),
        ) { cashFlow, totals, categoryList, accountList -> Sources(cashFlow, totals, categoryList, accountList) }

        val comparison = combine(
            balances.observeCashFlow(compare.current.first, compare.current.second).conflate(),
            balances.observeCashFlow(compare.previous.first, compare.previous.second).conflate(),
        ) { current, previous -> current to previous }

        return combine(
            sources,
            comparison,
            spending.observeMonthlyTotals(trendMonths.first(), trendMonths.last()).conflate(),
        ) { source, (currentFlow, previousFlow), monthly ->
            val chosen = chooseCurrency(currency, source.accounts, source.cashFlow)
            ReportOverview(
                period = shown,
                kind = kind,
                currency = chosen.selected,
                currencies = chosen.options,
                today = today,
                income = source.cashFlow.income(chosen.selected),
                expense = source.cashFlow.expense(chosen.selected),
                comparison = comparisonOf(
                    current = currentFlow.of(kind, chosen.selected),
                    previous = previousFlow.of(kind, chosen.selected),
                    previousPeriod = shown.previous(),
                    sameRange = compare.sameRange,
                ),
                shares = buildShares(source.totals, chosen.selected, source.categories, groupedByParent = true),
                trend = trendOf(trendMonths, monthly, kind, chosen.selected, today),
                accountIds = accountIdsFor(chosen.selected, source.accounts),
            )
        }
    }

    private class Sources(
        val cashFlow: CashFlow,
        val totals: List<CategoryTotal>,
        val categories: List<Category>,
        val accounts: List<Account>,
    )

    private class Ranges(
        val current: Pair<LocalDate, LocalDate>,
        val previous: Pair<LocalDate, LocalDate>,
        val sameRange: Boolean,
    )

    private class CurrencyChoice(val selected: String, val options: List<String>)

    /** R6: kỳ đang chạy so N ngày đầu với N ngày đầu của kỳ trước (kẹp ở cuối kỳ trước, ví dụ 31/3 → 28/2). */
    private fun comparisonRanges(period: ReportPeriod, today: LocalDate): Ranges {
        val previous = period.previous()
        if (today !in period) return Ranges(period.start to period.end, previous.start to previous.end, sameRange = false)
        val elapsed = period.start.daysUntil(today)
        val previousEnd = minOf(previous.start.plus(elapsed, DateTimeUnit.DAY), previous.end)
        return Ranges(period.start to today, previous.start to previousEnd, sameRange = true)
    }

    private fun comparisonOf(current: Money, previous: Money, previousPeriod: ReportPeriod, sameRange: Boolean): ReportComparison? {
        if (previous.amountMinor <= 0L) return null
        val change = (current.amountMinor.toDouble() - previous.amountMinor.toDouble()) / previous.amountMinor.toDouble() * 100.0
        return ReportComparison(previousPeriod, current, previous, change, sameRange)
    }

    private fun trendMonths(period: ReportPeriod): List<YearMonth> = when (period) {
        is ReportPeriod.Month -> (TREND_MONTHS - 1 downTo 0).map { period.month.minus(it, DateTimeUnit.MONTH) }
        is ReportPeriod.Year -> (1..MONTHS_IN_YEAR).map { YearMonth(period.year, it) }
    }

    private fun trendOf(
        months: List<YearMonth>,
        monthly: List<MonthlyTotal>,
        kind: CategoryKind,
        currency: String,
        today: LocalDate,
    ): List<TrendPoint> {
        val type = kind.toType()
        val byMonth = monthly.filter { it.type == type && it.total.currency == currency }.associate { it.month to it.total.amountMinor }
        val thisMonth = today.yearMonth
        return months.map { month ->
            TrendPoint(
                month = month,
                total = Money(byMonth[month] ?: 0L, currency),
                isPartial = month == thisMonth,
                isFuture = month > thisMonth,
            )
        }
    }

    /**
     * R2: chọn [requested] nếu còn trong danh sách, không thì tiền tệ của ví đang dùng đầu tiên, không thì tiền tệ có
     * phát sinh đầu tiên. Danh sách = tiền tệ của ví (theo thứ tự ví) rồi tới tiền tệ chỉ có trong dữ liệu kỳ này.
     */
    private fun chooseCurrency(requested: String?, accounts: List<Account>, cashFlow: CashFlow): CurrencyChoice {
        val active = accounts.filterNot { it.archived }.sortedBy { it.sortOrder }.map { it.currency }
        val options = (active + accounts.sortedBy { it.sortOrder }.map { it.currency } + cashFlow.currencies.sorted()).distinct()
        val selected = requested?.takeIf { it in options } ?: options.firstOrNull() ?: DEFAULT_CURRENCY
        return CurrencyChoice(selected, options.ifEmpty { listOf(selected) })
    }

    private fun CashFlow.of(kind: CategoryKind, currency: String): Money =
        if (kind == CategoryKind.EXPENSE) expense(currency) else income(currency)

    private fun CategoryKind.toType(): TransactionType =
        if (this == CategoryKind.EXPENSE) TransactionType.EXPENSE else TransactionType.INCOME

    private fun ReportPeriod.currentFor(today: LocalDate): ReportPeriod = when (this) {
        is ReportPeriod.Month -> ReportPeriod.monthOf(today)
        is ReportPeriod.Year -> ReportPeriod.yearOf(today)
    }

    private companion object {
        const val TREND_MONTHS = 6
        const val MONTHS_IN_YEAR = 12
        const val DEFAULT_CURRENCY = "VND"
    }
}
