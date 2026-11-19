package com.uit.finance.feature.capture.presentation.paste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.designsystem.component.FinanceTextField
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.NoticeBanner
import com.uit.finance.core.designsystem.component.OptionSheet
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SecondaryButton
import com.uit.finance.core.designsystem.component.SelectionField
import com.uit.finance.core.designsystem.component.SheetOption
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun PasteMessageRoute(
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: PasteMessageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PasteMessageEffect.Saved -> onBack()
                PasteMessageEffect.AddTransaction -> onAddTransaction()
            }
        }
    }
    PasteMessageScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
internal fun PasteMessageScreen(
    state: PasteMessageState,
    onIntent: (PasteMessageIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Column(modifier.fillMaxSize().imePadding()) {
        FinanceTopBar(title = "Dán tin nhắn ngân hàng", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                "Nhấn giữ vào ô dưới rồi chọn Dán. Ứng dụng không tự đọc bộ nhớ tạm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FinanceTextField(
                value = state.text,
                onValueChange = { onIntent(PasteMessageIntent.TextChanged(it)) },
                label = "Tin nhắn biến động số dư",
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            SelectionField(
                label = "Ngân hàng",
                value = state.bankName,
                icon = FinanceIcons.Bank,
                onClick = { onIntent(PasteMessageIntent.PickerOpened(PastePicker.BANK)) },
            )
            SecondaryButton(
                text = "Đọc tin",
                onClick = { onIntent(PasteMessageIntent.ReadClicked) },
                enabled = state.canRead,
                loading = state.isReading,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { NoticeBanner(message = it.resolve(), warning = true) }
            if (state.offerManualEntry) {
                SecondaryButton(
                    text = "Tự nhập giao dịch",
                    onClick = { onIntent(PasteMessageIntent.ManualEntryClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.preview != null) {
                Text(
                    if (state.isIncome) "Khoản thu · ${state.dateLabel}" else "Khoản chi · ${state.dateLabel}",
                    style = MaterialTheme.typography.titleMedium,
                )
                FinanceTextField(
                    value = state.amountInput,
                    onValueChange = { onIntent(PasteMessageIntent.AmountChanged(it)) },
                    label = "Số tiền (VND)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectionField(
                    label = "Ví",
                    value = state.accountName,
                    icon = FinanceIcons.Wallet,
                    onClick = { onIntent(PasteMessageIntent.PickerOpened(PastePicker.ACCOUNT)) },
                )
                SelectionField(
                    label = "Danh mục",
                    value = state.categoryName,
                    icon = FinanceIcons.Categories,
                    onClick = { onIntent(PasteMessageIntent.PickerOpened(PastePicker.CATEGORY)) },
                )
                FinanceTextField(
                    value = state.note,
                    onValueChange = { onIntent(PasteMessageIntent.NoteChanged(it)) },
                    label = "Ghi chú",
                    singleLine = false,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.canRemember) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.rememberAccount, onCheckedChange = { onIntent(PasteMessageIntent.RememberToggled(it)) })
                        Text("Lần sau dùng ví này cho ${state.bankName} •••${state.accountHint}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                PrimaryButton(
                    text = "Lưu giao dịch",
                    onClick = { onIntent(PasteMessageIntent.SaveClicked) },
                    enabled = state.canSave,
                    loading = state.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl),
                )
            }
        }
    }

    when (state.picker) {
        PastePicker.BANK -> OptionSheet<BankId?>(
            title = "Ngân hàng",
            options = listOf(SheetOption<BankId?>(key = "none", value = null, title = "Không rõ")) +
                state.banks.map { SheetOption<BankId?>(key = it.name, value = it, title = it.displayName) },
            selected = state.bank,
            onSelect = { onIntent(PasteMessageIntent.BankPicked(it)) },
            onDismiss = { onIntent(PasteMessageIntent.PickerDismissed) },
        )
        PastePicker.ACCOUNT -> OptionSheet(
            title = "Ví",
            options = state.accounts.map { SheetOption(key = it.id, value = it.id, title = it.name) },
            selected = state.accountId,
            onSelect = { onIntent(PasteMessageIntent.AccountPicked(it)) },
            onDismiss = { onIntent(PasteMessageIntent.PickerDismissed) },
        )
        PastePicker.CATEGORY -> OptionSheet(
            title = "Danh mục",
            options = state.categories.filter { it.income == state.isIncome }.map { SheetOption(key = it.id, value = it.id, title = it.name) },
            selected = state.categoryId,
            onSelect = { onIntent(PasteMessageIntent.CategoryPicked(it)) },
            onDismiss = { onIntent(PasteMessageIntent.PickerDismissed) },
        )
        null -> Unit
    }
}
