package com.uit.finance.feature.recurring.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountText
import com.uit.finance.core.designsystem.component.CircleIconButton
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceListItem
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LabelChip
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MessageSnackbarHost
import com.uit.finance.core.designsystem.component.RefreshContainer
import com.uit.finance.core.designsystem.component.SectionHeader
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ui.sync.SyncStatusIndicator
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RecurringListRoute(
    onBack: () -> Unit,
    onOpenEditor: (recurringId: String?) -> Unit,
    onOpenTransaction: (transactionId: String) -> Unit,
    viewModel: RecurringListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecurringListEffect.OpenEditor -> onOpenEditor(effect.recurringId)
                is RecurringListEffect.OpenTransaction -> onOpenTransaction(effect.transactionId)
                RecurringListEffect.Close -> onBack()
            }
        }
    }
    RecurringListScreen(state = state, onIntent = viewModel::onIntent)
}

/** Khoản định kỳ: cần chú ý, cần xác nhận, cần kiểm tra, sắp đến hạn 30 ngày, tất cả khoản. Kéo xuống để đồng bộ. */
@Composable
internal fun RecurringListScreen(
    state: RecurringListState,
    onIntent: (RecurringListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FinanceTopBar(
                title = "Khoản định kỳ",
                onBack = { onIntent(RecurringListIntent.BackClicked) },
                actions = {
                    SyncStatusIndicator(
                        overview = state.sync,
                        onRefresh = { onIntent(RecurringListIntent.SyncNow) },
                        onAcknowledgeIssues = { onIntent(RecurringListIntent.AcknowledgeSyncIssues) },
                    )
                    CircleIconButton(
                        icon = FinanceIcons.Add,
                        contentDescription = "Thêm khoản định kỳ",
                        onClick = { onIntent(RecurringListIntent.AddClicked) },
                    )
                },
            )
            RefreshContainer(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(RecurringListIntent.SyncNow) },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.isLoading -> LoadingIndicator()
                    state.isEmpty -> EmptyState(
                        icon = FinanceIcons.Calendar,
                        title = "Chưa có khoản định kỳ",
                        message = "Thêm tiền nhà, gói đăng ký hay lương: đến hạn app tự ghi sẵn giao dịch nháp để bạn xác nhận.",
                        actionLabel = "Thêm khoản định kỳ",
                        onAction = { onIntent(RecurringListIntent.AddClicked) },
                        modifier = horizontal,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        attention(state, onIntent, horizontal)
                        occurrences("confirm", "Cần xác nhận", state.toConfirm, state, onIntent, horizontal, reviewing = false)
                        occurrences("review", "Cần kiểm tra", state.toReview, state, onIntent, horizontal, reviewing = true)
                        upcoming(state, onIntent, horizontal)
                        all(state, onIntent, horizontal)
                    }
                }
            }
        }
        MessageSnackbarHost(
            message = state.message?.resolve(),
            onShown = { onIntent(RecurringListIntent.MessageShown) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.skipConfirm?.let { item ->
        ConfirmDialog(
            title = "Bỏ qua kỳ ${item.dateLabel} của \"${item.name}\"?",
            message = "Giao dịch nháp bị xoá và kỳ này sẽ không được tạo lại, kể cả trên máy khác.",
            confirmLabel = "Bỏ qua",
            dismissLabel = "Huỷ",
            destructive = true,
            onConfirm = { onIntent(RecurringListIntent.SkipConfirmed) },
            onDismiss = { onIntent(RecurringListIntent.SkipDismissed) },
        )
    }
}

private fun LazyListScope.header(key: String, title: String, modifier: Modifier) {
    item(key = "header-$key") { SectionHeader(title = title, modifier = modifier.padding(top = FinanceTheme.spacing.md)) }
}

private fun LazyListScope.attention(state: RecurringListState, onIntent: (RecurringListIntent) -> Unit, modifier: Modifier) {
    if (state.attention.isEmpty()) return
    header("attention", "Cần chú ý", modifier)
    items(state.attention, key = { "attention-${it.recurringId}" }) { item ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(FinanceTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(FinanceTheme.spacing.xs),
        ) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(FinanceTheme.spacing.sm)) {
                if (item.canResolve) {
                    TextButton(onClick = { onIntent(RecurringListIntent.BackfillClicked(item.recurringId)) }) { Text("Ghi bù thành nháp") }
                    TextButton(onClick = { onIntent(RecurringListIntent.SkipBlockedClicked(item.recurringId)) }) { Text("Bỏ qua các kỳ đó") }
                }
                TextButton(onClick = { onIntent(RecurringListIntent.RecurringClicked(item.recurringId)) }) { Text("Sửa khoản") }
            }
        }
    }
}

@Suppress("LongParameterList")
private fun LazyListScope.occurrences(
    key: String,
    title: String,
    list: List<OccurrenceUi>,
    state: RecurringListState,
    onIntent: (RecurringListIntent) -> Unit,
    modifier: Modifier,
    reviewing: Boolean,
) {
    if (list.isEmpty()) return
    header(key, title, modifier)
    if (reviewing) {
        item(key = "hint-$key") {
            Text(
                "Các nháp này được ghi trên máy này theo lịch cũ, trước khi lịch được sửa ở thiết bị khác.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
            )
        }
    }
    items(list, key = { "$key-${it.transactionId}" }) { item ->
        val busy = item.transactionId in state.busyIds
        FinanceListItem(
            title = item.name,
            subtitle = item.dateLabel,
            leading = { Tile(FinanceIcons.Receipt) },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Amount(item.amount)
                    Row(Modifier.alpha(if (busy) 0.4f else 1f)) {
                        if (!reviewing) {
                            TextButton(onClick = { onIntent(RecurringListIntent.ConfirmClicked(item.transactionId)) }, enabled = !busy) {
                                Text("Xác nhận")
                            }
                        }
                        TextButton(onClick = { onIntent(RecurringListIntent.SkipClicked(item.transactionId)) }, enabled = !busy) {
                            Text("Bỏ qua")
                        }
                    }
                }
            },
            onClick = { onIntent(RecurringListIntent.OccurrenceClicked(item.transactionId)) },
            modifier = modifier,
        )
    }
}

private fun LazyListScope.upcoming(state: RecurringListState, onIntent: (RecurringListIntent) -> Unit, modifier: Modifier) {
    if (state.upcoming.isEmpty()) return
    header("upcoming", "Sắp đến hạn (30 ngày)", modifier)
    items(state.upcomingTotals, key = { "total-$it" }) { line ->
        LabelChip(text = line, modifier = modifier)
    }
    state.upcoming.forEach { day ->
        item(key = "day-${day.dateLabel}") {
            Text(
                day.dateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(top = FinanceTheme.spacing.xs),
            )
        }
        items(day.items, key = { "upcoming-${it.key}" }) { item ->
            FinanceListItem(
                title = item.name,
                trailing = { Amount(item.amount) },
                onClick = { onIntent(RecurringListIntent.RecurringClicked(item.recurringId)) },
                modifier = modifier,
            )
        }
    }
}

private fun LazyListScope.all(state: RecurringListState, onIntent: (RecurringListIntent) -> Unit, modifier: Modifier) {
    if (state.recurrings.isEmpty()) return
    header("all", "Tất cả khoản định kỳ", modifier)
    items(state.recurrings, key = { "recurring-${it.id}" }) { item ->
        FinanceListItem(
            title = item.name,
            subtitle = "${item.schedule} · ${item.nextLabel}",
            leading = { Tile(if (item.amount.isIncome) FinanceIcons.Income else FinanceIcons.Expense) },
            trailing = {
                Column(horizontalAlignment = Alignment.End) {
                    Amount(item.amount)
                    if (item.needsAttention) LabelChip(text = "Cần chú ý")
                }
            },
            onClick = { onIntent(RecurringListIntent.RecurringClicked(item.id)) },
            modifier = modifier.alpha(if (item.isEnded) 0.5f else 1f),
        )
    }
}

@Composable
private fun Amount(money: MoneyUi) {
    AmountText(
        amount = money.amount,
        currency = money.currency,
        sign = if (money.isIncome) "+" else "-",
        amountColor = if (money.isIncome) FinanceTheme.colors.income else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Tile(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    IconTile(
        icon = icon,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
