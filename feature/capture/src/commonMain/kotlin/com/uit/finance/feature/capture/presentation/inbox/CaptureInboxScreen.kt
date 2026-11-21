package com.uit.finance.feature.capture.presentation.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.LabelChip
import com.uit.finance.core.designsystem.component.MessageSnackbarHost
import com.uit.finance.core.designsystem.component.OptionSheet
import com.uit.finance.core.designsystem.component.SectionHeader
import com.uit.finance.core.designsystem.component.SheetOption
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun CaptureInboxRoute(
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: CaptureInboxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CaptureInboxEffect.OpenTransaction -> onOpenTransaction(effect.transactionId)
                CaptureInboxEffect.AddTransaction -> onAddTransaction()
            }
        }
    }
    CaptureInboxScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

/**
 * Chờ xác nhận (plan mục 3.5): mọi giao dịch nháp — kể cả nháp từ máy khác và từ khoản định kỳ — rồi tin cần chọn ví,
 * rồi tin không đọc được.
 */
@Composable
internal fun CaptureInboxScreen(
    state: CaptureInboxState,
    onIntent: (CaptureInboxIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "top-bar") { FinanceTopBar(title = "Chờ xác nhận", onBack = onBack) }
            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = FinanceIcons.Inbox,
                        title = "Không có gì cần xác nhận",
                        message = "Giao dịch nháp từ khoản định kỳ và từ thông báo ngân hàng sẽ hiện ở đây.",
                        modifier = Modifier.padding(spacing.lg),
                    )
                }
            }
            if (state.drafts.isNotEmpty()) {
                item(key = "drafts-header") { SectionHeader("Cần xác nhận", Modifier.padding(horizontal = spacing.lg)) }
                items(state.drafts, key = { "d-" + it.transactionId }) { row ->
                    DraftCard(row, busy = row.transactionId in state.busyIds, onIntent = onIntent)
                }
            }
            if (state.needsAccount.isNotEmpty()) {
                item(key = "accounts-header") { SectionHeader("Cần chọn ví", Modifier.padding(horizontal = spacing.lg)) }
                items(state.needsAccount, key = { "a-" + it.eventId }) { row ->
                    InboxCard(
                        title = row.title,
                        trailing = row.amountText,
                        subtitle = row.subtitle,
                        busy = row.eventId in state.busyIds,
                        actions = listOf(
                            "Chọn ví" to { onIntent(CaptureInboxIntent.ChooseAccountClicked(row)) },
                            "Bỏ qua" to { onIntent(CaptureInboxIntent.DismissEventClicked(row.eventId)) },
                        ),
                    )
                }
            }
            if (state.unparsed.isNotEmpty()) {
                item(key = "unparsed-header") { SectionHeader("Không đọc được", Modifier.padding(horizontal = spacing.lg)) }
                items(state.unparsed, key = { "u-" + it.eventId }) { row ->
                    InboxCard(
                        title = row.bankName ?: "Tin ngân hàng",
                        trailing = null,
                        subtitle = row.preview,
                        busy = row.eventId in state.busyIds,
                        actions = listOf(
                            "Tự nhập" to { onIntent(CaptureInboxIntent.EnterManuallyClicked(row.eventId)) },
                            "Bỏ qua" to { onIntent(CaptureInboxIntent.DismissEventClicked(row.eventId)) },
                        ),
                    )
                }
            }
        }
        MessageSnackbarHost(
            message = state.message?.resolve(),
            onShown = { onIntent(CaptureInboxIntent.MessageShown) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.linking?.let { row ->
        OptionSheet(
            title = "Ví cho ${row.bank.displayName}" + (row.accountHint?.let { " •••$it" } ?: ""),
            options = state.accounts.map { SheetOption(key = it.id, value = it.id, title = it.name) },
            selected = null,
            onSelect = { onIntent(CaptureInboxIntent.AccountChosen(it)) },
            onDismiss = { onIntent(CaptureInboxIntent.AccountPickerDismissed) },
        )
    }
}

@Composable
private fun DraftCard(row: DraftRow, busy: Boolean, onIntent: (CaptureInboxIntent) -> Unit) {
    val chips = buildList {
        row.bankName?.let(::add)
        if (row.needsCheck) add("Kiểm tra kỹ")
        if (row.possibleDuplicate) add("Có thể trùng")
    }
    InboxCard(
        title = row.title,
        trailing = row.amountText,
        trailingIsIncome = row.isIncome,
        subtitle = row.subtitle,
        chips = chips,
        busy = busy,
        actions = listOf(
            "Xác nhận" to { onIntent(CaptureInboxIntent.ConfirmClicked(row.transactionId)) },
            "Sửa" to { onIntent(CaptureInboxIntent.EditClicked(row.transactionId)) },
            "Bỏ qua" to { onIntent(CaptureInboxIntent.DismissDraftClicked(row.transactionId)) },
        ),
    )
}

@Composable
private fun InboxCard(
    title: String,
    trailing: String?,
    subtitle: String,
    busy: Boolean,
    actions: List<Pair<String, () -> Unit>>,
    trailingIsIncome: Boolean = false,
    chips: List<String> = emptyList(),
) {
    val spacing = FinanceTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (trailing != null) {
                    Text(
                        trailing,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (trailingIsIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) { chips.forEach { LabelChip(it) } }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                actions.forEach { (label, action) -> TextButton(onClick = action, enabled = !busy) { Text(label) } }
            }
        }
    }
}
