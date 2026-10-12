package com.uit.finance.feature.accounts.presentation.editor

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
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountField
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.FinanceTextField
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.OptionSheet
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SegmentedControl
import com.uit.finance.core.designsystem.component.SelectionField
import com.uit.finance.core.designsystem.component.SheetOption
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ui.ledger.label
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun AccountEditorRoute(
    accountId: String?,
    onClose: () -> Unit,
    viewModel: AccountEditorViewModel = koinViewModel(key = "account-editor-$accountId") {
        parametersOf(AccountEditorArgs(accountId))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AccountEditorEffect.Close -> onClose()
            }
        }
    }
    AccountEditorScreen(state = state, onIntent = viewModel::onIntent)
}

/** Tạo / sửa ví: tên, loại, tiền tệ, số dư ban đầu (âm được: thẻ tín dụng, khoản đang nợ). */
@Composable
internal fun AccountEditorScreen(
    state: AccountEditorState,
    onIntent: (AccountEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Column(modifier.fillMaxSize()) {
        FinanceTopBar(
            title = if (state.isNew) "Tạo ví" else "Sửa ví",
            onBack = { onIntent(AccountEditorIntent.BackClicked) },
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
            state.error?.let { ErrorBanner(message = it, onDismiss = { onIntent(AccountEditorIntent.ErrorDismissed) }) }
            AmountField(
                value = state.openingBalance,
                onValueChange = { onIntent(AccountEditorIntent.OpeningBalanceChanged(it)) },
                currency = state.currency,
                label = "Số dư ban đầu",
                errorText = state.openingBalanceError?.resolve(),
            )
            FinanceTextField(
                value = state.name,
                onValueChange = { onIntent(AccountEditorIntent.NameChanged(it)) },
                label = "Tên ví",
                errorText = state.nameError?.resolve(),
                imeAction = ImeAction.Done,
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text("Loại ví", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedControl(
                    options = AccountType.entries,
                    selected = state.type,
                    onSelect = { onIntent(AccountEditorIntent.TypeChanged(it)) },
                    label = { it.label() },
                )
            }
            SelectionField(
                label = "Tiền tệ",
                value = CurrencyOptions.firstOrNull { it.code == state.currency }?.let { "${it.code} · ${it.name}" } ?: state.currency,
                icon = FinanceIcons.Coins,
                onClick = { onIntent(AccountEditorIntent.CurrencyClicked) },
                errorText = state.currencyError?.resolve(),
            )
            if (!state.isNew) {
                Text(
                    text = "Ví đã có giao dịch thì không đổi được tiền tệ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryButton(
            text = if (state.isNew) "Tạo ví" else "Lưu",
            onClick = { onIntent(AccountEditorIntent.SaveClicked) },
            enabled = state.canSave,
            loading = state.isSaving,
            modifier = Modifier.padding(spacing.lg),
        )
    }

    if (state.currencyPickerVisible) {
        OptionSheet(
            title = "Chọn tiền tệ",
            options = CurrencyOptions.map { SheetOption(key = it.code, value = it.code, title = it.code, subtitle = it.name) },
            selected = state.currency,
            onSelect = { onIntent(AccountEditorIntent.CurrencySelected(it)) },
            onDismiss = { onIntent(AccountEditorIntent.CurrencyPickerDismissed) },
            leading = {
                IconTile(
                    icon = FinanceIcons.Coins,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
    }
}
