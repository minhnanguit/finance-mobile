package com.uit.finance.feature.transactions.presentation.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountField
import com.uit.finance.core.designsystem.component.ChoiceTile
import com.uit.finance.core.designsystem.component.CircleIconButton
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.DatePickerSheet
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.FinanceTextField
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.OptionSheet
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SectionHeader
import com.uit.finance.core.designsystem.component.SegmentedControl
import com.uit.finance.core.designsystem.component.SelectionField
import com.uit.finance.core.designsystem.component.SheetOption
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.designsystem.theme.amountColor
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ui.ledger.categoryIconOrDefault
import com.uit.finance.core.ui.ledger.categoryTileColors
import com.uit.finance.core.ui.ledger.direction
import com.uit.finance.core.ui.ledger.icon
import com.uit.finance.core.ui.ledger.label
import com.uit.finance.core.ui.ledger.shortLabel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun TransactionEditorRoute(
    transactionId: String?,
    onClose: () -> Unit,
    onCreateAccount: () -> Unit,
    onOpenCategories: (CategoryKind) -> Unit,
    viewModel: TransactionEditorViewModel = koinViewModel(key = "transaction-editor-$transactionId") {
        parametersOf(TransactionEditorArgs(transactionId))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TransactionEditorEffect.Close -> onClose()
                TransactionEditorEffect.OpenCreateAccount -> onCreateAccount()
                is TransactionEditorEffect.OpenCategories -> onOpenCategories(effect.kind)
            }
        }
    }
    TransactionEditorScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun TransactionEditorScreen(
    state: TransactionEditorState,
    onIntent: (TransactionEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val amountFocus = remember { FocusRequester() }
    Column(modifier.fillMaxSize()) {
        FinanceTopBar(
            title = if (state.isNew) "Thêm giao dịch" else "Sửa giao dịch",
            onBack = { onIntent(TransactionEditorIntent.BackClicked) },
            actions = {
                if (!state.isNew) {
                    CircleIconButton(
                        icon = FinanceIcons.Delete,
                        contentDescription = "Xoá giao dịch",
                        onClick = { onIntent(TransactionEditorIntent.DeleteClicked) },
                    )
                }
            },
        )
        when {
            state.isLoading -> LoadingIndicator()
            !state.hasAccounts -> EmptyState(
                icon = FinanceIcons.Wallet,
                title = "Cần một ví trước",
                message = "Giao dịch luôn thuộc về một ví. Tạo ví đầu tiên rồi quay lại ghi chép.",
                actionLabel = "Tạo ví",
                onAction = { onIntent(TransactionEditorIntent.CreateAccountClicked) },
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
            else -> {
                EditorForm(state, onIntent, amountFocus, Modifier.weight(1f))
                PrimaryButton(
                    text = "Lưu",
                    onClick = { onIntent(TransactionEditorIntent.SaveClicked) },
                    enabled = state.canSave,
                    loading = state.isSaving,
                    modifier = Modifier.padding(spacing.lg),
                )
            }
        }
    }
    // Thêm mới: bật bàn phím số ngay để gõ số tiền không tốn thêm một chạm.
    LaunchedEffect(state.isLoading, state.hasAccounts) {
        if (state.isNew && !state.isLoading && state.hasAccounts) amountFocus.requestFocus()
    }
    EditorDialogs(state, onIntent)
}

@Composable
private fun EditorForm(
    state: TransactionEditorState,
    onIntent: (TransactionEditorIntent) -> Unit,
    amountFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        state.error?.let { ErrorBanner(message = it, onDismiss = { onIntent(TransactionEditorIntent.ErrorDismissed) }) }
        SegmentedControl(
            options = TransactionType.entries.sortedBy { it.order() },
            selected = state.type,
            onSelect = { onIntent(TransactionEditorIntent.TypeChanged(it)) },
            label = { it.shortLabel() },
        )
        AmountField(
            value = state.amount,
            onValueChange = { onIntent(TransactionEditorIntent.AmountChanged(it)) },
            currency = state.currency,
            label = "Số tiền",
            focusRequester = amountFocus,
            errorText = state.amountError?.resolve(),
            amountColor = state.type.direction().amountColor,
        )
        if (state.isTransfer) {
            TransferFields(state, onIntent)
        } else {
            CategoryGrid(state, onIntent)
            SelectionField(
                label = if (state.type == TransactionType.INCOME) "Vào ví" else "Từ ví",
                value = state.selectedAccount?.let { "${it.name} · ${it.currency}" } ?: "Chọn ví",
                icon = state.selectedAccount?.type?.icon() ?: FinanceIcons.Wallet,
                onClick = { onIntent(TransactionEditorIntent.PickerOpened(EditorPicker.Account)) },
                errorText = state.accountError?.resolve(),
            )
        }
        SelectionField(
            label = "Ngày",
            value = state.dateLabel,
            icon = FinanceIcons.Calendar,
            onClick = { onIntent(TransactionEditorIntent.PickerOpened(EditorPicker.Date)) },
            errorText = state.dateError?.resolve(),
        )
        FinanceTextField(
            value = state.payee,
            onValueChange = { onIntent(TransactionEditorIntent.PayeeChanged(it)) },
            label = if (state.type == TransactionType.INCOME) "Người trả (không bắt buộc)" else "Nơi chi / người nhận (không bắt buộc)",
            errorText = state.payeeError?.resolve(),
        )
        FinanceTextField(
            value = state.note,
            onValueChange = { onIntent(TransactionEditorIntent.NoteChanged(it)) },
            label = "Ghi chú (không bắt buộc)",
            errorText = state.noteError?.resolve(),
            singleLine = false,
            imeAction = ImeAction.Default,
        )
        Spacer(Modifier.padding(bottom = spacing.sm))
    }
}

@Composable
private fun TransferFields(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    val spacing = FinanceTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SelectionField(
            label = "Từ ví",
            value = state.selectedAccount?.let { "${it.name} · ${it.currency}" } ?: "Chọn ví",
            icon = state.selectedAccount?.type?.icon() ?: FinanceIcons.Wallet,
            onClick = { onIntent(TransactionEditorIntent.PickerOpened(EditorPicker.Account)) },
            errorText = state.accountError?.resolve(),
        )
        SelectionField(
            label = "Đến ví",
            value = state.selectedCounterAccount?.name
                ?: if (state.counterAccounts.isEmpty()) "Chưa có ví khác cùng tiền tệ" else "Chọn ví nhận",
            icon = state.selectedCounterAccount?.type?.icon() ?: FinanceIcons.Transfer,
            enabled = state.counterAccounts.isNotEmpty(),
            onClick = { onIntent(TransactionEditorIntent.PickerOpened(EditorPicker.CounterAccount)) },
            errorText = state.counterAccountError?.resolve(),
        )
    }
    AnimatedVisibility(visible = state.isNew, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        AmountField(
            value = state.fee,
            onValueChange = { onIntent(TransactionEditorIntent.FeeChanged(it)) },
            currency = state.currency,
            label = "Phí chuyển (không bắt buộc)",
            errorText = state.feeError?.resolve(),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/** Lưới danh mục 4 cột: một chạm là chọn (chạm thứ 2 của luồng ghi nhanh). Ô cuối mở màn quản lý danh mục. */
@Composable
private fun CategoryGrid(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    val spacing = FinanceTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeader(
            title = "Danh mục",
            actionLabel = "Quản lý",
            onAction = { onIntent(TransactionEditorIntent.ManageCategoriesClicked) },
        )
        if (state.categories.isEmpty()) {
            Text(
                text = "Chưa có danh mục ${if (state.type == TransactionType.INCOME) "thu" else "chi"}. Danh mục mặc định sẽ có sau lần đồng bộ đầu tiên, hoặc chạm \"Quản lý\" để tạo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.categories.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    val colors = categoryTileColors(category.colorHex, category.kind)
                    ChoiceTile(
                        icon = categoryIconOrDefault(category.iconKey, category.kind),
                        label = category.name,
                        selected = category.id == state.categoryId,
                        onClick = { onIntent(TransactionEditorIntent.CategorySelected(category.id)) },
                        containerColor = colors.container,
                        contentColor = colors.content,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        state.categoryError?.let {
            Text(it.resolve(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun EditorDialogs(state: TransactionEditorState, onIntent: (TransactionEditorIntent) -> Unit) {
    when (state.picker) {
        EditorPicker.Account -> AccountSheet(
            title = "Chọn ví",
            options = state.accounts,
            selected = state.accountId,
            onSelect = { onIntent(TransactionEditorIntent.AccountSelected(it)) },
            onDismiss = { onIntent(TransactionEditorIntent.PickerDismissed) },
        )
        EditorPicker.CounterAccount -> AccountSheet(
            title = "Chuyển đến ví",
            options = state.counterAccounts,
            selected = state.counterAccountId,
            onSelect = { onIntent(TransactionEditorIntent.CounterAccountSelected(it)) },
            onDismiss = { onIntent(TransactionEditorIntent.PickerDismissed) },
        )
        EditorPicker.Date -> DatePickerSheet(
            selected = state.occurredOn,
            range = state.dateRange,
            onSelect = { onIntent(TransactionEditorIntent.DateSelected(it)) },
            onDismiss = { onIntent(TransactionEditorIntent.PickerDismissed) },
        )
        null -> Unit
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = "Xoá giao dịch?",
            message = "Giao dịch sẽ bị xoá trên mọi thiết bị sau khi đồng bộ.",
            confirmLabel = "Xoá",
            dismissLabel = "Huỷ",
            destructive = true,
            onConfirm = { onIntent(TransactionEditorIntent.DeleteConfirmed) },
            onDismiss = { onIntent(TransactionEditorIntent.DeleteDismissed) },
        )
    }
}

@Composable
private fun AccountSheet(
    title: String,
    options: List<AccountOptionUi>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OptionSheet(
        title = title,
        options = options.map { SheetOption(key = it.id, value = it.id, title = it.name, subtitle = "${it.type.label()} · ${it.currency}") },
        selected = selected,
        onSelect = onSelect,
        onDismiss = onDismiss,
        leading = { id ->
            val account = options.first { it.id == id }
            IconTile(
                icon = account.type.icon(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

/** Chi trước (hay dùng nhất), rồi Thu, rồi Chuyển. */
private fun TransactionType.order(): Int = when (this) {
    TransactionType.EXPENSE -> 0
    TransactionType.INCOME -> 1
    TransactionType.TRANSFER -> 2
}

private const val GRID_COLUMNS = 4
