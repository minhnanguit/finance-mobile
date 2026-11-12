package com.uit.finance.feature.auth.presentation.signedout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.NoticeBanner
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SecondaryTextButton
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.feature.auth.domain.model.SignInMode
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SignedOutRoute(
    onSignedIn: () -> Unit,
    viewModel: SignedOutViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SignedOutEffect.NavigateToHome -> onSignedIn()
            }
        }
    }

    SignedOutScreen(state = state, onIntent = viewModel::onIntent)
}

/** Form đăng nhập / đăng ký nằm trên Keycloak (system browser); màn này chỉ có hai nút mở nó. */
@Composable
internal fun SignedOutScreen(
    state: SignedOutState,
    onIntent: (SignedOutIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Finance", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "Quản lý thu chi của bạn ở một nơi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.xl))

            state.notice?.let { notice ->
                NoticeBanner(
                    message = notice.resolve(),
                    icon = FinanceIcons.ShieldCheck,
                    onDismiss = { onIntent(SignedOutIntent.NoticeDismissed) },
                )
                Spacer(Modifier.height(spacing.md))
            }

            if (state.localDataWiped) {
                NoticeBanner(
                    message = "Đã xoá dữ liệu trên máy này.",
                    icon = FinanceIcons.ShieldCheck,
                    onDismiss = { onIntent(SignedOutIntent.LocalDataWipedDismissed) },
                )
                Spacer(Modifier.height(spacing.md))
            }

            state.error?.let { error ->
                ErrorBanner(message = error, onDismiss = { onIntent(SignedOutIntent.ErrorDismissed) })
                Spacer(Modifier.height(spacing.md))
            }

            PrimaryButton(
                text = "Đăng nhập",
                onClick = { onIntent(SignedOutIntent.SignInClicked) },
                enabled = !state.isBusy,
                loading = state.inProgress == SignInMode.SignIn,
            )
            Spacer(Modifier.height(spacing.sm))
            SecondaryTextButton(
                text = "Tạo tài khoản",
                onClick = { onIntent(SignedOutIntent.SignUpClicked) },
                enabled = !state.isBusy,
            )
        }
        // Ít khi cần nên đặt ở chân màn, xa nút chính: máy còn sổ của lần đăng nhập trước mà không còn phiên (B7, hoặc tài
        // khoản đã bị xoá từ máy khác — ADR-008 A9).
        if (state.localDataOnDevice) {
            SecondaryTextButton(
                text = "Xoá dữ liệu trên máy này",
                onClick = { onIntent(SignedOutIntent.WipeLocalDataClicked) },
                enabled = !state.isBusy,
                muted = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = spacing.lg),
            )
        }
    }

    if (state.wipeLocalDataPrompt) {
        ConfirmDialog(
            title = "Xoá dữ liệu trên máy này?",
            message = "Sổ thu chi của lần đăng nhập trước vẫn được giữ trên máy (đã mã hoá) để bạn đăng nhập lại và gửi nốt " +
                "thay đổi. Xoá thì các thay đổi chưa kịp đồng bộ sẽ mất. Dữ liệu trên máy chủ không bị ảnh hưởng.",
            confirmLabel = "Xoá",
            dismissLabel = "Giữ lại",
            destructive = true,
            onConfirm = { onIntent(SignedOutIntent.WipeLocalDataConfirmed) },
            onDismiss = { onIntent(SignedOutIntent.WipeLocalDataDismissed) },
        )
    }

    if (state.otherAccountsPrompt) {
        ConfirmDialog(
            title = "Máy này còn dữ liệu của tài khoản khác",
            message = "Sổ thu chi của tài khoản đăng nhập trước đó vẫn còn trên máy (đã mã hoá, tài khoản này không xem được). Xoá đi để giải phóng bộ nhớ và bảo vệ người kia?",
            confirmLabel = "Xoá",
            dismissLabel = "Giữ lại",
            destructive = true,
            onConfirm = { onIntent(SignedOutIntent.WipeOtherAccounts) },
            onDismiss = { onIntent(SignedOutIntent.KeepOtherAccounts) },
        )
    }
}
