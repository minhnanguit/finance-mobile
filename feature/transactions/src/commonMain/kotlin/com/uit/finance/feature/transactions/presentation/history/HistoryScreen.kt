package com.uit.finance.feature.transactions.presentation.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountText
import com.uit.finance.core.designsystem.component.CircleIconButton
import com.uit.finance.core.designsystem.component.DatePickerSheet
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FilterChipItem
import com.uit.finance.core.designsystem.component.FilterChipRow
import com.uit.finance.core.designsystem.component.FinanceFab
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MessageSnackbarHost
import com.uit.finance.core.designsystem.component.RefreshContainer
import com.uit.finance.core.designsystem.component.SearchField
import com.uit.finance.core.designsystem.component.SwipeToDeleteBox
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.designsystem.theme.MoneyDirection
import com.uit.finance.core.designsystem.theme.amountColor
import com.uit.finance.core.ui.sync.SyncStatusIndicator
import com.uit.finance.core.ui.transaction.TransactionListItem
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun HistoryRoute(
    onOpenEditor: (transactionId: String?) -> Unit,
    onOpenCategories: () -> Unit,
    args: HistoryArgs = HistoryArgs(),
    onBack: () -> Unit = {},
    viewModel: HistoryViewModel = koinViewModel { parametersOf(args) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryEffect.OpenEditor -> onOpenEditor(effect.transactionId)
                HistoryEffect.OpenCategories -> onOpenCategories()
                HistoryEffect.Close -> onBack()
            }
        }
    }
    // Rời màn (đổi tab, mở form, app xuống nền) thì xoá thật dòng đang chờ hoàn tác.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onIntent(HistoryIntent.CommitPendingDelete) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(HistoryIntent.Refresh)
        onPauseOrDispose { }
    }
    HistoryScreen(state = state, onIntent = viewModel::onIntent)
}

/**
 * Lịch sử gom theo ngày, vuốt trái để xoá (có hoàn tác), kéo xuống để đồng bộ. Trên cùng là ô tìm kiếm + nút lọc,
 * dưới đó là chip các điều kiện đang bật và dòng tổng của kết quả lọc.
 */
@Composable
internal fun HistoryScreen(
    state: HistoryState,
    onIntent: (HistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FinanceTopBar(
                title = "Giao dịch",
                onBack = if (state.showBack) ({ onIntent(HistoryIntent.BackClicked) }) else null,
                actions = {
                    SyncStatusIndicator(
                        overview = state.sync,
                        onRefresh = { onIntent(HistoryIntent.SyncNow) },
                        onAcknowledgeIssues = { onIntent(HistoryIntent.AcknowledgeSyncIssues) },
                    )
                    CircleIconButton(
                        icon = FinanceIcons.Categories,
                        contentDescription = "Danh mục",
                        onClick = { onIntent(HistoryIntent.CategoriesClicked) },
                    )
                },
            )
            SearchBar(state, onIntent, horizontal)
            FilterChipRow(
                chips = state.chips.map { FilterChipItem(key = it.key.name, label = it.label) },
                onRemove = { key -> onIntent(HistoryIntent.ChipRemoved(FilterChipKey.valueOf(key))) },
                onClearAll = { onIntent(HistoryIntent.ClearFilters) },
                contentPadding = PaddingValues(horizontal = spacing.lg),
                modifier = Modifier.padding(top = spacing.sm),
            )
            state.totals?.let { totals -> TotalsCard(totals, horizontal.padding(top = spacing.sm)) }
            RefreshContainer(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(HistoryIntent.SyncNow) },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.isLoading -> LoadingIndicator()
                    state.sections.isEmpty() && state.isFiltered -> EmptyState(
                        icon = FinanceIcons.Search,
                        title = "Không tìm thấy giao dịch nào",
                        message = "Thử từ khoá khác hoặc bỏ bớt điều kiện lọc.",
                        actionLabel = "Xoá lọc",
                        onAction = { onIntent(HistoryIntent.ClearFilters) },
                        modifier = horizontal.padding(top = spacing.md),
                    )
                    state.sections.isEmpty() -> EmptyState(
                        icon = FinanceIcons.Receipt,
                        title = "Chưa có giao dịch",
                        message = "Chạm + để ghi khoản thu chi đầu tiên.",
                        actionLabel = "Thêm giao dịch",
                        onAction = { onIntent(HistoryIntent.AddClicked) },
                        modifier = horizontal,
                    )
                    else -> HistoryList(state, onIntent)
                }
            }
        }
        FinanceFab(
            icon = FinanceIcons.Add,
            contentDescription = "Thêm giao dịch",
            onClick = { onIntent(HistoryIntent.AddClicked) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.lg),
        )
        val undoing = state.pendingDeleteId != null
        MessageSnackbarHost(
            message = if (undoing) "Đã xoá giao dịch" else state.message?.resolve(),
            actionLabel = if (undoing) "Hoàn tác" else null,
            onAction = { onIntent(HistoryIntent.UndoDelete) },
            onShown = { onIntent(if (undoing) HistoryIntent.CommitPendingDelete else HistoryIntent.MessageShown) },
            duration = SnackbarDuration.Short,
            // Nằm trên nút +, để nút "Hoàn tác" không bị nút nổi che.
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = spacing.xxl + spacing.xl),
        )
    }
    if (state.isFilterSheetOpen) HistoryFilterSheet(state, onIntent)
    state.datePicker?.let { picker ->
        DatePickerSheet(
            selected = picker.selected,
            range = picker.range,
            onSelect = { onIntent(HistoryIntent.CustomDateSelected(picker.bound, it)) },
            onDismiss = { onIntent(HistoryIntent.DatePickerDismissed) },
        )
    }
}

/** Ô tìm kiếm + nút mở sheet lọc; số trên nút là số điều kiện đang bật. */
@Composable
private fun SearchBar(state: HistoryState, onIntent: (HistoryIntent) -> Unit, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(
            value = state.query,
            onValueChange = { onIntent(HistoryIntent.QueryChanged(it)) },
            placeholder = "Tìm giao dịch",
            modifier = Modifier.weight(1f),
        )
        CircleIconButton(
            icon = FinanceIcons.Filter,
            contentDescription = "Bộ lọc",
            onClick = { onIntent(HistoryIntent.FilterClicked) },
            badgeCount = state.filter.activeCount,
        )
    }
}

/** "N giao dịch" rồi mỗi tiền tệ một dòng thu/chi: không bao giờ cộng lẫn tiền tệ (F10). */
@Composable
private fun TotalsCard(totals: HistoryTotalsUi, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(totals.summary, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        totals.currencies.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
                line.income?.let { TotalAmount(label = "Thu", amount = it, currency = line.currency, direction = MoneyDirection.In) }
                line.expense?.let { TotalAmount(label = "Chi", amount = it, currency = line.currency, direction = MoneyDirection.Out) }
            }
        }
    }
}

@Composable
private fun TotalAmount(label: String, amount: String, currency: String, direction: MoneyDirection) {
    val spacing = FinanceTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(amount = amount, currency = currency, amountColor = direction.amountColor, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun HistoryList(state: HistoryState, onIntent: (HistoryIntent) -> Unit) {
    val spacing = FinanceTheme.spacing
    val listState = rememberLazyListState()
    // Còn ~10 dòng nữa là hết thì nạp thêm, để user cuộn liền mạch.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, state.canLoadMore) {
        if (nearEnd && state.canLoadMore) onIntent(HistoryIntent.LoadMore)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // Đáy chừa chỗ để nút + không che dòng cuối.
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.xxl + spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        state.sections.forEach { section ->
            item(key = "day-${section.key}") {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.animateItem().padding(top = spacing.md, bottom = spacing.xs),
                )
            }
            items(section.items, key = { it.id }) { item ->
                SwipeToDeleteBox(
                    onDelete = { onIntent(HistoryIntent.DeleteSwiped(item.id)) },
                    modifier = Modifier.animateItem(),
                ) {
                    TransactionListItem(item = item, onClick = { onIntent(HistoryIntent.ItemClicked(item.id)) })
                }
            }
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 10
