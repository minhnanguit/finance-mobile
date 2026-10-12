package com.uit.finance.feature.transactions.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.CircleIconButton
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceFab
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MessageSnackbarHost
import com.uit.finance.core.designsystem.component.RefreshContainer
import com.uit.finance.core.designsystem.component.SwipeToDeleteBox
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ui.sync.SyncStatusIndicator
import com.uit.finance.core.ui.transaction.TransactionListItem
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun HistoryRoute(
    onOpenEditor: (transactionId: String?) -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryEffect.OpenEditor -> onOpenEditor(effect.transactionId)
                HistoryEffect.OpenCategories -> onOpenCategories()
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

/** Tab Giao dịch: lịch sử gom theo ngày, vuốt trái để xoá (có hoàn tác), kéo xuống để đồng bộ. */
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
            RefreshContainer(
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(HistoryIntent.SyncNow) },
                modifier = Modifier.weight(1f),
            ) {
                when {
                    state.isLoading -> LoadingIndicator()
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
