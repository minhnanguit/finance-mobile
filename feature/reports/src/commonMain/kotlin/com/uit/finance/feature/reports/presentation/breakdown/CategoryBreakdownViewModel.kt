package com.uit.finance.feature.reports.presentation.breakdown

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.feature.reports.domain.model.CategoryBreakdown
import com.uit.finance.feature.reports.domain.usecase.ObserveCategoryBreakdownUseCase
import com.uit.finance.feature.reports.presentation.common.ReportFormatter
import com.uit.finance.feature.reports.presentation.common.ShareAction
import com.uit.finance.feature.reports.presentation.common.ShareRowUi
import com.uit.finance.feature.reports.presentation.navigation.ReportTransactionsQuery
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal class CategoryBreakdownViewModel(
    private val args: CategoryBreakdownArgs,
    observeBreakdown: ObserveCategoryBreakdownUseCase,
    private val format: ReportFormatter,
) : MviViewModel<CategoryBreakdownState, CategoryBreakdownIntent, CategoryBreakdownEffect>(CategoryBreakdownState()) {

    /** Ví của tiền tệ đang xem, theo lần phát mới nhất (xem [ReportTransactionsQuery.accountIds]). */
    private var accountIds: Set<String>? = null

    init {
        val period = args.period
        if (period == null) {
            setState { copy(isLoading = false, isEmpty = true) }
        } else {
            setState { copy(periodLabel = format.periodLabel(period), kind = args.kind, currency = args.currency) }
            observeBreakdown(args.categoryId, period, args.kind, args.currency)
                .onEach { breakdown ->
                    accountIds = breakdown.accountIds
                    setState { map(breakdown) }
                }
                .launchIn(viewModelScope)
        }
    }

    override fun onIntent(intent: CategoryBreakdownIntent) = when (intent) {
        is CategoryBreakdownIntent.OpenRow -> if (intent.row.action == ShareAction.OpenTransactions) openTransactions(intent.row.categoryId) else Unit
        CategoryBreakdownIntent.OpenAll -> openTransactions(args.categoryId)
        CategoryBreakdownIntent.Back -> sendEffect(CategoryBreakdownEffect.NavigateBack)
    }

    private fun openTransactions(categoryId: String?) {
        val period = args.period ?: return
        sendEffect(CategoryBreakdownEffect.OpenTransactions(ReportTransactionsQuery(period.start, period.end, categoryId, args.kind, accountIds)))
    }

    private fun CategoryBreakdownState.map(breakdown: CategoryBreakdown): CategoryBreakdownState {
        val rows: List<ShareRowUi> = breakdown.shares.map { share ->
            val direct = share.categoryId == args.categoryId
            // Lịch sử lọc theo danh mục cha luôn gồm cả con, nên phần "ghi thẳng vào danh mục cha" không mở riêng được.
            format.rowOf(
                share = share,
                kind = args.kind,
                action = if (direct) ShareAction.None else ShareAction.OpenTransactions,
                subtitle = if (direct) "Ghi thẳng vào danh mục này" else null,
            )
        }
        val kindLabel = if (args.kind == CategoryKind.EXPENSE) "Chi" else "Thu"
        return copy(
            isLoading = false,
            title = breakdown.parent?.name ?: "Danh mục",
            total = format.full(breakdown.total),
            totalCompact = format.compact(breakdown.total),
            slices = rows.zip(breakdown.shares) { row, share -> format.sliceOf(row, share.total.amountMinor.toDouble()) },
            rows = rows,
            donutDescription = buildString {
                append("$kindLabel ${format.compact(breakdown.total)} ${args.currency}.")
                rows.forEach { append(" ${it.name} ${it.percentLabel}.") }
            },
            isEmpty = breakdown.total == Money(0, args.currency),
            animationKey = "${args.period}|${args.kind}|${args.currency}",
        )
    }
}
