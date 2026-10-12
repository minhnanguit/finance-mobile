package com.uit.finance.feature.accounts.presentation.list

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountText
import com.uit.finance.core.designsystem.component.CircleIconButton
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceListItem
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MenuAction
import com.uit.finance.core.designsystem.component.MessageSnackbarHost
import com.uit.finance.core.designsystem.component.OverflowMenu
import com.uit.finance.core.designsystem.component.SectionHeader
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ui.ledger.icon
import com.uit.finance.core.ui.ledger.label
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AccountListRoute(
    onBack: () -> Unit,
    onOpenEditor: (accountId: String?) -> Unit,
    viewModel: AccountListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AccountListEffect.OpenEditor -> onOpenEditor(effect.accountId)
                AccountListEffect.NavigateBack -> onBack()
            }
        }
    }
    AccountListScreen(state = state, onIntent = viewModel::onIntent)
}

/** Danh sách ví: tổng theo tiền tệ, ví đang dùng (đổi thứ tự, lưu trữ, xoá), ví đã lưu trữ thu gọn bên dưới. */
@Composable
internal fun AccountListScreen(
    state: AccountListState,
    onIntent: (AccountListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FinanceTopBar(
                title = "Ví của bạn",
                onBack = { onIntent(AccountListIntent.BackClicked) },
                actions = {
                    CircleIconButton(
                        icon = FinanceIcons.Add,
                        contentDescription = "Thêm ví",
                        onClick = { onIntent(AccountListIntent.AddClicked) },
                    )
                },
            )
            when {
                state.isLoading -> LoadingIndicator()
                state.active.isEmpty() && state.archived.isEmpty() -> EmptyState(
                    icon = FinanceIcons.Wallet,
                    title = "Chưa có ví nào",
                    message = "Tạo ví đầu tiên (tiền mặt, ngân hàng, ví điện tử) để bắt đầu ghi chép.",
                    actionLabel = "Tạo ví",
                    onAction = { onIntent(AccountListIntent.AddClicked) },
                    modifier = horizontal,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    item(key = "totals") { TotalsCard(state.totals, horizontal.padding(bottom = spacing.md)) }
                    items(state.active, key = { it.id }) { item ->
                        AccountRow(item, onIntent, Modifier.animateItem().then(horizontal))
                    }
                    if (state.archived.isNotEmpty()) {
                        item(key = "archived-header") {
                            SectionHeader(
                                title = "Đã lưu trữ (${state.archived.size})",
                                actionLabel = if (state.showArchived) "Ẩn" else "Hiện",
                                onAction = { onIntent(AccountListIntent.ToggleArchived) },
                                modifier = horizontal.padding(top = spacing.md),
                            )
                        }
                        if (state.showArchived) {
                            items(state.archived, key = { it.id }) { item ->
                                AccountRow(item, onIntent, Modifier.animateItem().then(horizontal))
                            }
                        }
                    }
                }
            }
        }
        MessageSnackbarHost(
            message = state.message?.resolve(),
            onShown = { onIntent(AccountListIntent.MessageShown) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.pendingDelete?.let { account ->
        ConfirmDialog(
            title = "Xoá ví \"${account.name}\"?",
            message = "Chỉ xoá được ví chưa có giao dịch. Ví đã dùng thì hãy lưu trữ để giữ lịch sử.",
            confirmLabel = "Xoá",
            dismissLabel = "Huỷ",
            destructive = true,
            onConfirm = { onIntent(AccountListIntent.DeleteConfirmed) },
            onDismiss = { onIntent(AccountListIntent.DeleteDismissed) },
        )
    }
}

@Composable
private fun TotalsCard(totals: List<MoneyUi>, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(spacing.lg)
            .animateContentSize(FinanceMotion.standard()),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text("Tổng số dư", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        if (totals.isEmpty()) {
            Text("—", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        totals.forEach { total ->
            AmountText(
                amount = total.amount,
                currency = total.currency,
                sign = if (total.isNegative) "-" else "",
                amountColor = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineMedium,
                currencyStyle = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun AccountRow(item: AccountItemUi, onIntent: (AccountListIntent) -> Unit, modifier: Modifier = Modifier) {
    val actions = buildList {
        add(MenuAction("Sửa", FinanceIcons.Edit) { onIntent(AccountListIntent.EditClicked(item.id)) })
        if (item.canMoveUp) add(MenuAction("Lên trên", FinanceIcons.MoveUp) { onIntent(AccountListIntent.MoveUpClicked(item.id)) })
        if (item.canMoveDown) add(MenuAction("Xuống dưới", FinanceIcons.MoveDown) { onIntent(AccountListIntent.MoveDownClicked(item.id)) })
        if (item.archived) {
            add(MenuAction("Bỏ lưu trữ", FinanceIcons.Unarchive) { onIntent(AccountListIntent.ArchiveClicked(item.id, archived = false)) })
        } else {
            add(MenuAction("Lưu trữ", FinanceIcons.Archive) { onIntent(AccountListIntent.ArchiveClicked(item.id, archived = true)) })
        }
        add(MenuAction("Xoá", FinanceIcons.Delete, destructive = true) { onIntent(AccountListIntent.DeleteClicked(item.id)) })
    }
    FinanceListItem(
        title = item.name,
        subtitle = item.type.label(),
        leading = {
            IconTile(
                icon = item.type.icon(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountText(
                    amount = item.balance.amount,
                    currency = item.balance.currency,
                    sign = if (item.balance.isNegative) "-" else "",
                    amountColor = if (item.balance.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                OverflowMenu(actions = actions, contentDescription = "Thao tác với ví ${item.name}")
            }
        },
        onClick = { onIntent(AccountListIntent.EditClicked(item.id)) },
        modifier = modifier,
    )
}
