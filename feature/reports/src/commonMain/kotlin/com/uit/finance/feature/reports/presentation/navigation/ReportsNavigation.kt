package com.uit.finance.feature.reports.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.ReportPeriod
import com.uit.finance.feature.reports.presentation.breakdown.CategoryBreakdownRoute
import com.uit.finance.feature.reports.presentation.overview.ReportsRoute
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.serialization.Serializable

/** Màn Báo cáo, mở từ Trang chủ (nút "Báo cáo" hoặc thẻ "Tháng này"). */
@Serializable data object ReportsDestination

/**
 * Danh mục cha tách ra từng danh mục con. Route chỉ mang kiểu cơ bản: [period] là `yyyy-MM` hoặc `yyyy`, [kind] là
 * tên `CategoryKind`. Dựng bằng [of].
 */
@Serializable
data class CategoryBreakdownDestination(
    val categoryId: String,
    val period: String,
    val kind: String,
    val currency: String,
) {
    internal fun toPeriod(): ReportPeriod? =
        YearMonth.Formats.ISO.parseOrNull(period)?.let(ReportPeriod::Month) ?: period.toIntOrNull()?.let(ReportPeriod::Year)

    internal fun toKind(): CategoryKind = CategoryKind.entries.firstOrNull { it.name == kind } ?: CategoryKind.EXPENSE

    companion object {
        fun of(categoryId: String, period: ReportPeriod, kind: CategoryKind, currency: String) = CategoryBreakdownDestination(
            categoryId = categoryId,
            period = when (period) {
                is ReportPeriod.Month -> period.month.toString()
                is ReportPeriod.Year -> period.year.toString()
            },
            kind = kind.name,
            currency = currency,
        )
    }
}

/**
 * "Xem các giao dịch này": app map sang lịch sử đã lọc sẵn của feature giao dịch. [categoryId] là danh mục cha thì
 * lịch sử gồm cả danh mục con. [accountIds] = các ví của tiền tệ đang xem (giao dịch mang tiền tệ của ví), để lịch sử
 * không lẫn tiền tệ khác; `null` khi mọi ví cùng một tiền tệ.
 */
data class ReportTransactionsQuery(
    val from: LocalDate,
    val to: LocalDate,
    val categoryId: String?,
    val kind: CategoryKind,
    val accountIds: Set<String>? = null,
)

/** Hợp đồng navigation: app quyết định đi *đâu*, feature quyết định *khi nào*. */
fun NavGraphBuilder.reportsNavGraph(
    onBack: () -> Unit,
    onOpenBreakdown: (CategoryBreakdownDestination) -> Unit,
    onOpenTransactions: (ReportTransactionsQuery) -> Unit,
    onAddTransaction: () -> Unit,
) {
    composable<ReportsDestination> {
        ReportsRoute(
            onBack = onBack,
            onOpenBreakdown = onOpenBreakdown,
            onOpenTransactions = onOpenTransactions,
            onAddTransaction = onAddTransaction,
        )
    }
    composable<CategoryBreakdownDestination> { entry ->
        CategoryBreakdownRoute(
            destination = entry.toRoute<CategoryBreakdownDestination>(),
            onBack = onBack,
            onOpenTransactions = onOpenTransactions,
        )
    }
}
