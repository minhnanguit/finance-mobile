package com.uit.finance.feature.reports.presentation.overview

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.feature.reports.domain.model.ReportComparison
import com.uit.finance.feature.reports.domain.model.ReportOverview
import com.uit.finance.feature.reports.domain.model.saturatingMinus
import com.uit.finance.feature.reports.presentation.common.ReportFormatter
import com.uit.finance.feature.reports.presentation.common.ShareAction
import com.uit.finance.feature.reports.presentation.common.ShareRowUi
import com.uit.finance.feature.reports.presentation.common.ShareStyle
import com.uit.finance.feature.reports.presentation.common.SliceUi
import kotlin.math.abs
import kotlin.math.roundToLong

/** [ReportOverview] → [ReportsState]. Giữ nguyên các lựa chọn của người dùng đang có trong [current]. */
class ReportsUiMapper(private val format: ReportFormatter) {

    internal fun map(overview: ReportOverview, current: ReportsState): ReportsState {
        val kind = overview.kind
        val currency = overview.currency
        val rows = overview.top.map { format.rowOf(it, kind, it.action()) }
        val others = overview.others.map { format.rowOf(it, kind, it.action()) }
        val othersRow = if (others.isEmpty()) null else othersRow(overview, kind)
        val allRows = rows + listOfNotNull(othersRow)
        val remaining = Money(overview.income.amountMinor.saturatingMinus(overview.expense.amountMinor), currency)
        val counterpart = if (kind == CategoryKind.EXPENSE) "Thu ${format.compact(overview.income)}" else "Chi ${format.compact(overview.expense)}"
        val kindLabel = if (kind == CategoryKind.EXPENSE) "Chi" else "Thu"

        return current.copy(
            isLoading = false,
            mode = if (overview.period is ReportPeriod.Year) ReportMode.Year else ReportMode.Month,
            kind = kind,
            periodLabel = format.periodLabel(overview.period),
            canGoNext = overview.canGoNext,
            currency = currency,
            currencyOptions = overview.currencies,
            total = format.full(overview.total),
            totalCompact = format.compact(overview.total),
            comparison = overview.comparison?.let { comparisonUi(it, kind, overview.period) },
            counterpartLabel = counterpart,
            remainingLabel = "Còn lại ${format.compact(remaining)}",
            remainingNegative = remaining.amountMinor < 0,
            slices = overview.top.mapIndexed { index, share -> format.sliceOf(rows[index], share.total.amountMinor.toDouble()) } +
                listOfNotNull(othersRow?.let { SliceUi(it.key, overview.othersTotal.amountMinor.toDouble(), null, ShareStyle.Other) }),
            rows = allRows,
            otherRows = others,
            showOthers = current.showOthers && others.isNotEmpty(),
            donutDescription = buildString {
                append("$kindLabel ${format.compact(overview.total)} $currency.")
                allRows.forEach { append(" ${it.name} ${it.percentLabel}.") }
            },
            trendTitle = if (overview.period is ReportPeriod.Year) "12 tháng của năm" else "6 tháng gần đây",
            trend = overview.trend.map { point ->
                val isSelected = overview.period is ReportPeriod.Month && overview.period.month == point.month
                TrendBarUi(
                    label = "T${point.month.month.ordinal + 1}",
                    value = point.total.amountMinor.toDouble(),
                    isSelected = isSelected,
                    isPartial = point.isPartial,
                    isFuture = point.isFuture,
                    actionLabel = if (isSelected || point.isFuture) null else "Xem tháng ${point.month.month.ordinal + 1}/${point.month.year}",
                )
            },
            trendDescription = overview.trend.filterNot { it.isFuture }.joinToString(
                prefix = "$kindLabel theo tháng: ",
                separator = ", ",
            ) { "tháng ${it.month.month.ordinal + 1} ${format.compact(it.total)}" },
            isEmpty = overview.isEmpty,
            isKindEmpty = overview.shares.isEmpty(),
            animationKey = "${overview.period}|$kind|$currency",
        )
    }

    private fun othersRow(overview: ReportOverview, kind: CategoryKind) = ShareRowUi(
        key = ReportFormatter.OTHERS_KEY,
        categoryId = null,
        name = "Khác",
        amount = format.full(overview.othersTotal),
        currency = overview.currency,
        percentLabel = format.percentLabel(overview.othersPercent),
        fraction = (overview.othersPercent / 100.0).toFloat(),
        colorHex = null,
        iconKey = null,
        kind = kind,
        style = ShareStyle.Other,
        action = ShareAction.ToggleOthers,
        subtitle = "${overview.others.size} danh mục",
    )

    private fun comparisonUi(comparison: ReportComparison, kind: CategoryKind, period: ReportPeriod): ComparisonUi {
        val rounded = comparison.changePercent.roundToLong()
        val direction = when {
            rounded > 0 -> ChangeDirection.Up
            rounded < 0 -> ChangeDirection.Down
            else -> ChangeDirection.Flat
        }
        val previous = format.shortPeriodLabel(comparison.previousPeriod, period)
        val against = if (comparison.sameRange) "cùng kỳ $previous" else previous
        val text = when (direction) {
            ChangeDirection.Flat -> "Gần bằng $against"
            else -> "${if (abs(rounded) > MAX_SHOWN_CHANGE) "> $MAX_SHOWN_CHANGE" else abs(rounded)}% so với $against"
        }
        val isGood = when (direction) {
            ChangeDirection.Flat -> true
            ChangeDirection.Up -> kind == CategoryKind.INCOME
            ChangeDirection.Down -> kind == CategoryKind.EXPENSE
        }
        return ComparisonUi(text, direction, isGood)
    }

    private fun com.uit.finance.feature.reports.domain.model.CategoryShare.action(): ShareAction =
        if (hasChildren) ShareAction.OpenBreakdown else ShareAction.OpenTransactions

    private companion object {
        const val MAX_SHOWN_CHANGE = 999L
    }
}
