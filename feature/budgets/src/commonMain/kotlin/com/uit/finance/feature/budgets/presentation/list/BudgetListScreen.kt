package com.uit.finance.feature.budgets.presentation.list

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountText
import com.uit.finance.core.designsystem.component.BudgetProgressBar
import com.uit.finance.core.designsystem.component.CircleIconButton
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LabelChip
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MessageSnackbarHost
import com.uit.finance.core.designsystem.component.ProgressTone
import com.uit.finance.core.designsystem.component.RefreshContainer
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ui.ledger.categoryIconOrDefault
import com.uit.finance.core.ui.ledger.categoryTileColors
import com.uit.finance.core.ui.sync.SyncStatusIndicator
import com.uit.finance.feature.budgets.domain.model.BudgetTone
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun BudgetListRoute(
    onOpenEditor: (budgetId: String?) -> Unit,
    viewModel: BudgetListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BudgetListEffect.OpenEditor -> onOpenEditor(effect.budgetId)
            }
        }
    }
    // Quay lại từ nền: đọc lại "hôm nay" ngay, không đợi tới mốc nửa đêm kế tiếp (G12).
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(BudgetListIntent.Resumed)
        onPauseOrDispose { }
    }
    BudgetListScreen(state = state, onIntent = viewModel::onIntent)
}

/** Tab Ngân sách: ‹ tháng ›, thẻ tổng theo tiền tệ, danh sách gần vượt nhất lên đầu, kéo xuống để đồng bộ. */
@Composable
internal fun BudgetListScreen(
    state: BudgetListState,
    onIntent: (BudgetListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FinanceTopBar(
                title = "Ngân sách",
                actions = {
                    SyncStatusIndicator(
                        overview = state.sync,
                        onRefresh = { onIntent(BudgetListIntent.SyncNow) },
                        onAcknowledgeIssues = { onIntent(BudgetListIntent.AcknowledgeSyncIssues) },
                    )
                    CircleIconButton(
                        icon = FinanceIcons.Add,
                        contentDescription = "Tạo ngân sách",
                        onClick = { onIntent(BudgetListIntent.AddClicked) },
                    )
                },
            )
            MonthSwitcher(state, onIntent, horizontal.padding(bottom = spacing.sm))
            RefreshContainer(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(BudgetListIntent.SyncNow) },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.isLoading -> LoadingIndicator()
                    state.items.isEmpty() -> EmptyState(
                        icon = FinanceIcons.Budget,
                        title = if (state.isCurrentMonth) "Chưa có ngân sách" else "Tháng này chưa có ngân sách",
                        message = "Đặt mức chi tối đa mỗi tháng cho từng danh mục để biết mình đã tiêu bao nhiêu, còn bao nhiêu.",
                        actionLabel = "Tạo ngân sách",
                        onAction = { onIntent(BudgetListIntent.AddClicked) },
                        modifier = horizontal,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        item(key = "totals") { TotalsCard(state.totals, horizontal.padding(bottom = spacing.sm)) }
                        items(state.items, key = { it.id }) { item ->
                            BudgetRow(
                                item = item,
                                onClick = { onIntent(BudgetListIntent.ItemClicked(item.id)) },
                                modifier = Modifier.animateItem().then(horizontal),
                            )
                        }
                    }
                }
            }
        }
        MessageSnackbarHost(
            message = state.message?.resolve(),
            onShown = { onIntent(BudgetListIntent.MessageShown) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MonthSwitcher(state: BudgetListState, onIntent: (BudgetListIntent) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(
            icon = FinanceIcons.ChevronLeft,
            contentDescription = "Tháng trước",
            onClick = { onIntent(BudgetListIntent.PreviousMonth) },
        )
        Text(
            text = state.monthLabel,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        // Không xem được tháng tương lai: giữ chỗ để tiêu đề không lệch, nhưng ẩn và bỏ khỏi trình đọc màn hình.
        val nextModifier = if (state.canGoNext) Modifier else Modifier.alpha(0f).clearAndSetSemantics { }
        CircleIconButton(
            icon = FinanceIcons.ChevronRight,
            contentDescription = "Tháng sau",
            onClick = { if (state.canGoNext) onIntent(BudgetListIntent.NextMonth) },
            modifier = nextModifier,
        )
    }
}

@Composable
private fun TotalsCard(totals: List<BudgetTotalUi>, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(spacing.lg)
            .animateContentSize(FinanceMotion.standard()),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // Xếp dọc, mỗi số một dòng đủ bề ngang: ba cột ngang hàng bị cắt "VND 3.000.0…" ngay trên máy 1080px.
        totals.forEach { total ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                TotalRow("Ngân sách", total.budgeted, content)
                TotalRow("Đã chi", total.spent, content)
                TotalRow(
                    label = if (total.remaining.isNegative) "Vượt" else "Còn lại",
                    money = total.remaining,
                    color = if (total.remaining.isNegative) MaterialTheme.colorScheme.error else content,
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, money: MoneyUi, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f).padding(end = FinanceTheme.spacing.sm),
        )
        AmountText(
            amount = money.amount,
            currency = money.currency,
            amountColor = color,
            style = MaterialTheme.typography.titleMedium,
            currencyStyle = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BudgetRow(item: BudgetItemUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    val toneColor = item.tone.color()
    val tile = categoryTileColors(item.categoryColor, CategoryKind.EXPENSE)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            IconTile(
                icon = categoryIconOrDefault(item.categoryIcon, CategoryKind.EXPENSE),
                containerColor = tile.container,
                contentColor = tile.content,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = item.categoryName ?: "Danh mục không còn",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.categoryArchived) LabelChip("Đã lưu trữ")
            }
            Text(item.percentLabel, style = MaterialTheme.typography.titleMedium, color = toneColor)
        }
        BudgetProgressBar(progress = item.progress, tone = item.tone.toProgressTone())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Đã chi ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountText(amount = item.spent.amount, currency = item.spent.currency, style = MaterialTheme.typography.bodyMedium)
            Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountText(
                amount = item.budgeted.amount,
                currency = item.budgeted.currency,
                style = MaterialTheme.typography.bodyMedium,
                amountColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = remainingLine(item),
            style = MaterialTheme.typography.bodySmall,
            color = if (item.remaining.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Còn lại VND 450.000 · VND 25.000/ngày" hoặc "Vượt VND 300.000". */
private fun remainingLine(item: BudgetItemUi): String {
    val remaining = "${item.remaining.currency} ${item.remaining.amount}"
    if (item.remaining.isNegative) return "Vượt $remaining"
    val daily = item.dailyAllowance?.let { " · ${it.currency} ${it.amount}/ngày" }.orEmpty()
    return "Còn lại $remaining$daily"
}

private fun BudgetTone.toProgressTone(): ProgressTone = when (this) {
    BudgetTone.Normal -> ProgressTone.Normal
    BudgetTone.Warning -> ProgressTone.Warning
    BudgetTone.Over -> ProgressTone.Over
}

@Composable
private fun BudgetTone.color(): Color = when (this) {
    BudgetTone.Normal -> MaterialTheme.colorScheme.onSurface
    BudgetTone.Warning -> FinanceTheme.colors.warning
    BudgetTone.Over -> MaterialTheme.colorScheme.error
}
