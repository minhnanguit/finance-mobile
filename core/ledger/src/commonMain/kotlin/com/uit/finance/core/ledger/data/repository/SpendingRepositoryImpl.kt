package com.uit.finance.core.ledger.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.domain.model.CategorySpend
import com.uit.finance.core.ledger.domain.repository.SpendingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.YearMonth

/** Một câu SUM gom theo danh mục gốc (LedgerTransaction.sq `selectExpenseByRootCategory`), cho ngân sách. */
internal class SpendingRepositoryImpl(
    private val store: LedgerStore,
    private val dispatchers: DispatcherProvider,
) : SpendingRepository {

    override fun observeMonthlyExpense(month: YearMonth): Flow<List<CategorySpend>> = store.observe(emptyList()) { database ->
        database.ledgerTransactionQueries
            .selectExpenseByRootCategory(fromDate = month.firstDay.toString(), toDate = month.lastDay.toString())
            .asFlow().mapToList(dispatchers.io)
            .map { rows ->
                rows.mapNotNull { row ->
                    row.root_category_id?.let { CategorySpend(categoryId = it, spent = Money(row.total_minor, row.currency)) }
                }
            }
    }
}
