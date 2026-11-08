package com.uit.finance.feature.reports.domain.usecase

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.SpendingRepository
import com.uit.finance.feature.reports.domain.model.CategoryBreakdown
import com.uit.finance.feature.reports.domain.model.saturatingPlus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate

/**
 * Danh mục cha [parentId] tách ra từng danh mục con trong [period] (đào sâu từ màn Báo cáo). Phần ghi thẳng vào danh
 * mục cha là một dòng riêng. Tổng bằng đúng nhóm của danh mục đó ở màn tổng quan, vì cùng query, chỉ khác cách gom.
 */
class ObserveCategoryBreakdownUseCase(
    private val spending: SpendingRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {
    operator fun invoke(parentId: String, period: ReportPeriod, kind: CategoryKind, currency: String): Flow<CategoryBreakdown> =
        combine(
            spending.observeCategoryTotals(period.start, period.end, kind, groupByParent = false).conflate(),
            categories.observeCategories().conflate(),
            accounts.observeAccounts().conflate(),
        ) { totals, categoryList, accountList ->
            val family = categoryList.filter { it.id == parentId || it.parentId == parentId }
            val familyIds = family.mapTo(mutableSetOf()) { it.id }
            val shares = buildShares(totals.filter { it.categoryId in familyIds }, currency, family, groupedByParent = false)
            CategoryBreakdown(
                parent = categoryList.firstOrNull { it.id == parentId },
                total = Money(shares.fold(0L) { sum, it -> sum.saturatingPlus(it.total.amountMinor) }, currency),
                shares = shares,
                accountIds = accountIdsFor(currency, accountList),
            )
        }
}
