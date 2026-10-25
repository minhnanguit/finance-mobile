package com.uit.finance.feature.transactions.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.feature.transactions.domain.model.HistoryFilter
import com.uit.finance.feature.transactions.presentation.categories.CategoriesRoute
import com.uit.finance.feature.transactions.presentation.categoryeditor.CategoryEditorRoute
import com.uit.finance.feature.transactions.presentation.editor.TransactionEditorRoute
import com.uit.finance.feature.transactions.presentation.history.HistoryArgs
import com.uit.finance.feature.transactions.presentation.history.HistoryRoute
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Tab Giao dịch (lịch sử). */
@Serializable data object TransactionsDestination

/**
 * Lịch sử đã lọc sẵn, mở như màn con từ màn khác (báo cáo, ngân sách: "xem các giao dịch này"). Chỉ mang kiểu cơ bản
 * (F11): ngày ISO `yyyy-MM-dd`, id, tên `TransactionType`. **Không** mang từ khoá, để từ khoá không bị lưu vào
 * saved-state của navigation. Dựng bằng [of] cho đúng định dạng.
 */
@Serializable
data class FilteredTransactionsDestination(
    val from: String? = null,
    val to: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val type: String? = null,
) {
    companion object {
        fun of(
            from: LocalDate? = null,
            to: LocalDate? = null,
            categoryId: String? = null,
            accountId: String? = null,
            type: TransactionType? = null,
        ) = FilteredTransactionsDestination(from?.toString(), to?.toString(), categoryId, accountId, type?.name)
    }
}

/** Thêm (`transactionId == null`) hoặc sửa giao dịch. */
@Serializable data class TransactionEditorDestination(val transactionId: String? = null)

/** Quản lý danh mục; [kind] là tên `CategoryKind` mở sẵn (route chỉ mang kiểu cơ bản). */
@Serializable data class CategoriesDestination(val kind: String = CategoryKind.EXPENSE.name)

/** Tạo (`categoryId == null`, loại [kind]) hoặc sửa danh mục. */
@Serializable data class CategoryEditorDestination(val categoryId: String? = null, val kind: String = CategoryKind.EXPENSE.name)

/** Hợp đồng navigation: app quyết định đi *đâu*, feature quyết định *khi nào*. */
fun NavGraphBuilder.transactionsNavGraph(
    onBack: () -> Unit,
    onOpenTransaction: (transactionId: String?) -> Unit,
    onOpenCategories: (CategoryKind) -> Unit,
    onOpenCategory: (categoryId: String?, kind: CategoryKind) -> Unit,
    onCreateAccount: () -> Unit,
) {
    composable<TransactionsDestination> {
        HistoryRoute(onOpenEditor = onOpenTransaction, onOpenCategories = { onOpenCategories(CategoryKind.EXPENSE) })
    }
    composable<FilteredTransactionsDestination> { entry ->
        HistoryRoute(
            onOpenEditor = onOpenTransaction,
            onOpenCategories = { onOpenCategories(CategoryKind.EXPENSE) },
            args = HistoryArgs(initialFilter = entry.toRoute<FilteredTransactionsDestination>().toFilter(), showBack = true),
            onBack = onBack,
        )
    }
    composable<TransactionEditorDestination> { entry ->
        TransactionEditorRoute(
            transactionId = entry.toRoute<TransactionEditorDestination>().transactionId,
            onClose = onBack,
            onCreateAccount = onCreateAccount,
            onOpenCategories = onOpenCategories,
        )
    }
    composable<CategoriesDestination> { entry ->
        CategoriesRoute(
            initialKind = entry.toRoute<CategoriesDestination>().kind.toCategoryKind(),
            onBack = onBack,
            onOpenEditor = onOpenCategory,
        )
    }
    composable<CategoryEditorDestination> { entry ->
        val destination = entry.toRoute<CategoryEditorDestination>()
        CategoryEditorRoute(categoryId = destination.categoryId, kind = destination.kind.toCategoryKind(), onClose = onBack)
    }
}

/** Tham số hỏng (ngày sai định dạng, loại lạ) thì bỏ qua điều kiện đó thay vì crash. */
private fun FilteredTransactionsDestination.toFilter() = HistoryFilter.prefilled(
    from = from?.let(LocalDate.Formats.ISO::parseOrNull),
    to = to?.let(LocalDate.Formats.ISO::parseOrNull),
    categoryId = categoryId,
    accountId = accountId,
    type = TransactionType.entries.firstOrNull { it.name == type },
)

private fun String.toCategoryKind(): CategoryKind = CategoryKind.entries.firstOrNull { it.name == this } ?: CategoryKind.EXPENSE
