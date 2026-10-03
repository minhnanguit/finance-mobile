package com.uit.finance.feature.auth.presentation.signedout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SecondaryTextButton
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
    Column(
        modifier = modifier
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
}
