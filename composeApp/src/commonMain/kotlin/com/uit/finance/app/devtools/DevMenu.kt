package com.uit.finance.app.devtools

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toUiText
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private sealed interface DevMessage {
    data class Info(val text: String) : DevMessage
    data class Error(val text: UiText) : DevMessage
}

/**
 * Nút "DEV" nổi (kéo được để không che UI đang soi) mở bottom sheet công cụ dev. Chỉ gọi khi
 * `isDebugBuild` và đã đăng nhập: [DemoDataSeeder] chỉ có trong graph Koin của build debug.
 */
@Composable
internal fun DevMenu(modifier: Modifier = Modifier) {
    val seeder = koinInject<DemoDataSeeder>()
    val scope = rememberCoroutineScope()
    var hidden by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<DevMessage?>(null) }
    if (hidden) return

    fun run(action: suspend () -> AppResult<LedgerCounts>, describe: (LedgerCounts) -> String) {
        if (running) return
        running = true
        message = null
        scope.launch {
            try {
                message = action().fold(
                    onSuccess = { DevMessage.Info(describe(it)) },
                    onFailure = { DevMessage.Error(it.toUiText()) },
                )
            } finally {
                running = false
            }
        }
    }

    DevBadge(onClick = { sheetOpen = true }, modifier = modifier)

    if (sheetOpen) {
        DevSheet(
            running = running,
            message = message,
            onSeed = { run(seeder::seed) { "Đã tạo ${it.accounts} ví và ${it.transactions} giao dịch." } },
            onClear = { confirmClear = true },
            onHide = {
                sheetOpen = false
                hidden = true
            },
            onDismissMessage = { message = null },
            onDismiss = { sheetOpen = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Xoá toàn bộ ví và giao dịch?",
            message = "Xoá mọi ví và giao dịch của tài khoản đang đăng nhập, kể cả dữ liệu tự nhập, " +
                "và đồng bộ việc xoá lên server. Danh mục giữ nguyên.",
            confirmLabel = "Xoá",
            dismissLabel = "Huỷ",
            onConfirm = {
                confirmClear = false
                run(seeder::clear) { "Đã xoá ${it.accounts} ví và ${it.transactions} giao dịch." }
            },
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }
}

@Composable
private fun DevBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onTertiary,
        shadowElevation = 4.dp,
        modifier = modifier
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); drag += amount } }
            .size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("DEV", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevSheet(
    running: Boolean,
    message: DevMessage?,
    onSeed: () -> Unit,
    onClear: () -> Unit,
    onHide: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = FinanceTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = spacing.lg, end = spacing.lg, bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text("Công cụ dev", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Chỉ có ở bản debug. Dữ liệu ghi qua repository nên vào outbox và đồng bộ lên server như dữ liệu thật.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (message) {
                is DevMessage.Info -> Text(message.text, color = MaterialTheme.colorScheme.primary)
                is DevMessage.Error -> ErrorBanner(message = message.text, onDismiss = onDismissMessage)
                null -> Unit
            }
            PrimaryButton(text = "Tạo dữ liệu mẫu", onClick = onSeed, loading = running)
            OutlinedButton(onClick = onClear, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Text("Xoá toàn bộ ví và giao dịch")
            }
            TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
                Text("Ẩn nút DEV tới lần mở app sau")
            }
        }
    }
}
