package com.uit.finance.feature.recurring.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uit.finance.feature.recurring.presentation.editor.RecurringEditorRoute
import com.uit.finance.feature.recurring.presentation.list.RecurringListRoute
import kotlinx.serialization.Serializable

/** Màn "Khoản định kỳ" (mở từ Trang chủ). */
@Serializable data object RecurringDestination

/** Tạo (`recurringId == null`) hoặc sửa khoản định kỳ. */
@Serializable data class RecurringEditorDestination(val recurringId: String? = null)

/**
 * Hợp đồng navigation: app quyết định đi *đâu*, feature quyết định *khi nào*. Mở một giao dịch nháp để sửa số tiền/ngày
 * trước khi xác nhận là màn của feature giao dịch, nên đi qua [onOpenTransaction].
 */
fun NavGraphBuilder.recurringNavGraph(
    onBack: () -> Unit,
    onOpenEditor: (recurringId: String?) -> Unit,
    onOpenTransaction: (transactionId: String) -> Unit,
) {
    composable<RecurringDestination> {
        RecurringListRoute(onBack = onBack, onOpenEditor = onOpenEditor, onOpenTransaction = onOpenTransaction)
    }
    composable<RecurringEditorDestination> { entry ->
        RecurringEditorRoute(recurringId = entry.toRoute<RecurringEditorDestination>().recurringId, onClose = onBack)
    }
}
