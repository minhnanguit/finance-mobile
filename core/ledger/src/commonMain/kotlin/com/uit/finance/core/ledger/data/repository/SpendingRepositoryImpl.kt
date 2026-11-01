package com.uit.finance.core.ledger.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.data.local.toLong
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.CategorySpend
import com.uit.finance.core.ledger.domain.model.CategoryTotal
import com.uit.finance.core.ledger.domain.model.MonthlyTotal
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.SpendingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** Câu SUM ở `LedgerReport.sq` (docs/REPORTS-PLAN.md C1). Ngân sách và báo cáo dùng chung, không ai viết query riêng. */
internal class SpendingRepositoryImpl(
    private val store: LedgerStore,
    private val dispatchers: DispatcherProvider,
) : SpendingRepository {

    /** C2: chính là [observeCategoryTotals] của tháng đó, gộp con vào cha, bỏ nhóm không có danh mục. */
    override fun observeMonthlyExpense(month: YearMonth): Flow<List<CategorySpend>> =
        observeCategoryTotals(month.firstDay, month.lastDay, CategoryKind.EXPENSE, groupByParent = true).map { totals ->
            totals.mapNotNull { total -> total.categoryId?.let { CategorySpend(categoryId = it, spent = total.total) } }
        }

    override fun observeCategoryTotals(
        from: LocalDate,
        to: LocalDate,
        kind: CategoryKind,
        groupByParent: Boolean,
    ): Flow<List<CategoryTotal>> = store.observe(emptyList()) { database ->
        database.ledgerReportQueries
            .selectCategoryTotals(
                groupByParent = groupByParent.toLong(),
                type = kind.toTransactionType().name,
                fromDate = from.toString(),
                toDate = to.toString(),
            )
            .asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { CategoryTotal(categoryId = it.group_id, total = Money(it.total_minor, it.currency)) } }
    }

    override fun observeMonthlyTotals(from: YearMonth, to: YearMonth): Flow<List<MonthlyTotal>> = store.observe(emptyList()) { database ->
        database.ledgerReportQueries
            .selectMonthlyTotals(fromDate = from.firstDay.toString(), toDate = to.lastDay.toString())
            .asFlow().mapToList(dispatchers.io)
            .map { rows ->
                rows.mapNotNull { row ->
                    val month = YearMonth.Formats.ISO.parseOrNull(row.month) ?: return@mapNotNull null
                    MonthlyTotal(month = month, type = TransactionType.valueOf(row.type), total = Money(row.total_minor, row.currency))
                }
            }
    }

    private fun CategoryKind.toTransactionType(): TransactionType = when (this) {
        CategoryKind.INCOME -> TransactionType.INCOME
        CategoryKind.EXPENSE -> TransactionType.EXPENSE
    }
}
