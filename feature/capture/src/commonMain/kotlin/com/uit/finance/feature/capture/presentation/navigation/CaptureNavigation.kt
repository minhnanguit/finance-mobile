package com.uit.finance.feature.capture.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.uit.finance.feature.capture.presentation.inbox.CaptureInboxRoute
import com.uit.finance.feature.capture.presentation.paste.PasteMessageRoute
import com.uit.finance.feature.capture.presentation.setup.CaptureSetupRoute
import kotlinx.serialization.Serializable

/** Cài đặt "Tự ghi từ thông báo ngân hàng", mở từ tab Cá nhân. */
@Serializable data object CaptureSetupDestination

/** Chờ xác nhận: mở từ cài đặt hoặc khi bấm thông báo "Có N giao dịch cần xác nhận". */
@Serializable data object CaptureInboxDestination

@Serializable data object PasteMessageDestination

/**
 * Hợp đồng navigation: app quyết định đi *đâu*, feature quyết định *khi nào*.
 *
 * @param onOpenTransaction sửa một giao dịch nháp (editor của feature/transactions)
 * @param onAddTransaction tự nhập khi tin không đọc được; `note` là nội dung tin đã che số để điền sẵn
 */
fun NavGraphBuilder.captureNavGraph(
    onBack: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenPaste: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: (note: String?) -> Unit,
) {
    composable<CaptureSetupDestination> {
        CaptureSetupRoute(onBack = onBack, onOpenInbox = onOpenInbox, onOpenPaste = onOpenPaste)
    }
    composable<CaptureInboxDestination> {
        CaptureInboxRoute(onBack = onBack, onOpenTransaction = onOpenTransaction, onAddTransaction = onAddTransaction)
    }
    composable<PasteMessageDestination> {
        PasteMessageRoute(onBack = onBack, onAddTransaction = onAddTransaction)
    }
}
