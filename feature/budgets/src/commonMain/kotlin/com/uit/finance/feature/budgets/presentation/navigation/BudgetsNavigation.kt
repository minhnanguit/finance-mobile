package com.uit.finance.feature.budgets.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uit.finance.feature.budgets.presentation.editor.BudgetEditorRoute
import com.uit.finance.feature.budgets.presentation.list.BudgetListRoute
import kotlinx.serialization.Serializable

/** Tab Ngân sách. */
@Serializable data object BudgetsDestination

/** Tạo (`budgetId == null`) hoặc sửa ngân sách. */
@Serializable data class BudgetEditorDestination(val budgetId: String? = null)

/** Hợp đồng navigation: app quyết định đi *đâu* (mở form, quay lại), feature quyết định *khi nào*. */
fun NavGraphBuilder.budgetsNavGraph(
    onBack: () -> Unit,
    onOpenEditor: (budgetId: String?) -> Unit,
) {
    composable<BudgetsDestination> {
        BudgetListRoute(onOpenEditor = onOpenEditor)
    }
    composable<BudgetEditorDestination> { entry ->
        BudgetEditorRoute(budgetId = entry.toRoute<BudgetEditorDestination>().budgetId, onClose = onBack)
    }
}
