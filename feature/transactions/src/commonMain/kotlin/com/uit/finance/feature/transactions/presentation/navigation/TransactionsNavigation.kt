package com.uit.finance.feature.transactions.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.feature.transactions.presentation.categories.CategoriesRoute
import com.uit.finance.feature.transactions.presentation.categoryeditor.CategoryEditorRoute
import com.uit.finance.feature.transactions.presentation.editor.TransactionEditorRoute
import com.uit.finance.feature.transactions.presentation.history.HistoryRoute
import kotlinx.serialization.Serializable

/** Tab Giao dịch (lịch sử). */
@Serializable data object TransactionsDestination

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

private fun String.toCategoryKind(): CategoryKind = CategoryKind.entries.firstOrNull { it.name == this } ?: CategoryKind.EXPENSE
