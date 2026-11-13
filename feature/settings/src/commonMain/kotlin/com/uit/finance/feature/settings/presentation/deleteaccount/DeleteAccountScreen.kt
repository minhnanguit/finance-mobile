package com.uit.finance.feature.settings.presentation.deleteaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.DangerButton
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.FinanceTextField
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.NoticeBanner
import com.uit.finance.core.designsystem.component.SettingsGroup
import com.uit.finance.core.designsystem.component.SettingsItem
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.feature.settings.domain.model.DeletionConfirmation
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun DeleteAccountRoute(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DeleteAccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                DeleteAccountEffect.Deleted -> onDeleted()
                DeleteAccountEffect.NavigateBack -> onBack()
            }
        }
    }
    DeleteAccountScreen(state = state, onIntent = viewModel::onIntent)
}

/**
 * Giải thích rõ cái gì mất trước khi cho xoá (ADR-008 §1): không khôi phục được, xoá trên mọi máy, thay đổi chưa
 * gửi cũng mất. Nút xoá chỉ sáng khi đã gõ XOÁ; bấm thì mở trình duyệt để đăng nhập lại.
 */
@Composable
internal fun DeleteAccountScreen(
    state: DeleteAccountState,
    onIntent: (DeleteAccountIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Column(modifier.fillMaxSize()) {
        FinanceTopBar(title = "Xoá tài khoản", onBack = { onIntent(DeleteAccountIntent.BackClicked) })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                IconTile(
                    icon = FinanceIcons.Warning,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.large,
                )
                Text(
                    text = "Xoá vĩnh viễn tài khoản",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                state.email?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            SettingsGroup(title = "Sẽ bị xoá") {
                SettingsItem(
                    icon = FinanceIcons.Wallet,
                    title = "Ví, danh mục và mọi giao dịch",
                    subtitle = "Trên máy chủ và trên mọi máy đang dùng tài khoản này",
                )
                SettingsItem(
                    icon = FinanceIcons.Key,
                    title = "Tài khoản đăng nhập",
                    subtitle = "Email và mật khẩu không dùng được nữa",
                )
                SettingsItem(
                    icon = FinanceIcons.LocalData,
                    title = "Dữ liệu trên máy này",
                    subtitle = "Sổ thu chi đã mã hoá và phiên đăng nhập",
                )
            }

            NoticeBanner(
                message = buildString {
                    append("Không thể khôi phục sau khi xoá.")
                    if (state.unsentChanges > 0) {
                        append(" ${state.unsentChanges} thay đổi chưa đồng bộ trên máy này cũng sẽ mất.")
                    }
                },
                icon = FinanceIcons.Warning,
                warning = true,
            )

            FinanceTextField(
                value = state.confirmation,
                onValueChange = { onIntent(DeleteAccountIntent.ConfirmationChanged(it)) },
                label = "Gõ ${DeletionConfirmation.WORD} để xác nhận",
                enabled = !state.isDeleting,
                imeAction = ImeAction.Done,
            )

            Text(
                text = "Bấm xoá sẽ mở trang đăng nhập để xác nhận đó là bạn. Dữ liệu trên máy chủ được xoá hẳn trong " +
                    "vòng 15 phút.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Ghim đáy như nút chính của các form khác: luôn thấy, kể cả khi bàn phím đang mở; lỗi nằm ngay trên nút.
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            state.error?.let { error ->
                ErrorBanner(message = error, onDismiss = { onIntent(DeleteAccountIntent.ErrorDismissed) })
            }
            DangerButton(
                text = "Xác nhận và xoá tài khoản",
                onClick = { onIntent(DeleteAccountIntent.Submit) },
                enabled = state.canSubmit,
                loading = state.isDeleting,
            )
        }
    }
}
