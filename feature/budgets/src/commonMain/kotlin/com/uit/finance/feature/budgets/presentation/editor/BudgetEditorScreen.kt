package com.uit.finance.feature.budgets.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountField
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MenuAction
import com.uit.finance.core.designsystem.component.OptionSheet
import com.uit.finance.core.designsystem.component.OverflowMenu
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SelectionField
import com.uit.finance.core.designsystem.component.SheetOption
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ui.ledger.categoryIconOrDefault
import com.uit.finance.core.ui.ledger.categoryTileColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun BudgetEditorRoute(
    budgetId: String?,
    onClose: () -> Unit,
    viewModel: BudgetEditorViewModel = koinViewModel(key = "budget-editor-$budgetId") {
        parametersOf(BudgetEditorArgs(budgetId))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BudgetEditorEffect.Close -> onClose()
            }
        }
    }
    BudgetEditorScreen(state = state, onIntent = viewModel::onIntent)
}

/** Tạo ngân sách (danh mục + tiền tệ + số tiền mỗi tháng) hoặc sửa số tiền; xoá qua menu ⋮. */
@Composable
internal fun BudgetEditorScreen(
    state: BudgetEditorState,
    onIntent: (BudgetEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Column(modifier.fillMaxSize()) {
        FinanceTopBar(
            title = if (state.isNew) "Tạo ngân sách" else "Sửa ngân sách",
            onBack = { onIntent(BudgetEditorIntent.BackClicked) },
            actions = {
                if (!state.isNew) {
                    OverflowMenu(
                        actions = listOf(
                            MenuAction("Xoá", FinanceIcons.Delete, destructive = true) { onIntent(BudgetEditorIntent.DeleteClicked) },
                        ),
                        contentDescription = "Thao tác với ngân sách",
                    )
                }
            },
        )
        if (state.isLoading) {
            LoadingIndicator()
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            state.error?.let { ErrorBanner(message = it, onDismiss = { onIntent(BudgetEditorIntent.ErrorDismissed) }) }
            AmountField(
                value = state.amount,
                onValueChange = { onIntent(BudgetEditorIntent.AmountChanged(it)) },
                currency = state.currency,
                label = "Mức chi tối đa mỗi tháng",
                errorText = state.amountError?.resolve(),
            )
            SelectionField(
                label = "Danh mục",
                value = state.categoryName ?: "Chọn danh mục chi",
                icon = FinanceIcons.Categories,
                onClick = { onIntent(BudgetEditorIntent.CategoryClicked) },
                enabled = state.isNew,
                errorText = state.categoryError?.resolve(),
            )
            if (state.isNew && state.categoryOptions.isEmpty()) {
                Hint("Mọi danh mục chi đang dùng đã có ngân sách ở ${state.currency}.")
            }
            if (state.canChooseCurrency || !state.isNew) {
                SelectionField(
                    label = "Tiền tệ",
                    value = state.currency,
                    icon = FinanceIcons.Coins,
                    onClick = { onIntent(BudgetEditorIntent.CurrencyClicked) },
                    enabled = state.canChooseCurrency,
                )
            }
            Hint(
                if (state.isNew) {
                    "Áp dụng từ tháng này và lặp lại mỗi tháng. Chi tiêu của danh mục con được cộng vào danh mục cha."
                } else {
                    "Số tiền mới áp cho mọi tháng. Muốn đổi danh mục hay tiền tệ thì xoá ngân sách này rồi tạo mới."
                },
            )
        }
        PrimaryButton(
            text = if (state.isNew) "Tạo ngân sách" else "Lưu",
            onClick = { onIntent(BudgetEditorIntent.SaveClicked) },
            enabled = state.canSave,
            loading = state.isSaving,
            modifier = Modifier.padding(spacing.lg),
        )
    }

    if (state.categoryPickerVisible) {
        OptionSheet(
            title = "Chọn danh mục",
            options = state.categoryOptions.map { SheetOption(key = it.id, value = it.id, title = it.name) },
            selected = state.categoryId,
            onSelect = { onIntent(BudgetEditorIntent.CategorySelected(it)) },
            onDismiss = { onIntent(BudgetEditorIntent.CategoryPickerDismissed) },
            leading = { id ->
                val option = state.categoryOptions.firstOrNull { it.id == id }
                val tile = categoryTileColors(option?.color, CategoryKind.EXPENSE)
                IconTile(
                    icon = categoryIconOrDefault(option?.icon, CategoryKind.EXPENSE),
                    containerColor = tile.container,
                    contentColor = tile.content,
                )
            },
        )
    }

    if (state.currencyPickerVisible) {
        OptionSheet(
            title = "Chọn tiền tệ",
            options = state.currencyOptions.map { SheetOption(key = it, value = it, title = it) },
            selected = state.currency,
            onSelect = { onIntent(BudgetEditorIntent.CurrencySelected(it)) },
            onDismiss = { onIntent(BudgetEditorIntent.CurrencyPickerDismissed) },
        )
    }

    if (state.deleteConfirmVisible) {
        ConfirmDialog(
            title = "Xoá ngân sách \"${state.categoryName ?: ""}\"?",
            message = "Ngân sách bị xoá ở mọi tháng. Giao dịch của danh mục vẫn giữ nguyên.",
            confirmLabel = "Xoá",
            dismissLabel = "Huỷ",
            destructive = true,
            onConfirm = { onIntent(BudgetEditorIntent.DeleteConfirmed) },
            onDismiss = { onIntent(BudgetEditorIntent.DeleteDismissed) },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
