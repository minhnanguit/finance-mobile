package com.uit.finance.feature.auth.presentation.signedout

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.FinanceLogo
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SecondaryOutlinedButton
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.feature.auth.generated.resources.Res
import com.uit.finance.feature.auth.generated.resources.welcome_wallet
import com.uit.finance.feature.auth.generated.resources.other_accounts_delete
import com.uit.finance.feature.auth.generated.resources.other_accounts_keep
import com.uit.finance.feature.auth.generated.resources.other_accounts_message
import com.uit.finance.feature.auth.generated.resources.other_accounts_title
import com.uit.finance.feature.auth.generated.resources.signed_out_brand
import com.uit.finance.feature.auth.generated.resources.signed_out_create_account
import com.uit.finance.feature.auth.generated.resources.signed_out_security
import com.uit.finance.feature.auth.generated.resources.signed_out_sign_in
import com.uit.finance.feature.auth.generated.resources.signed_out_subtitle
import com.uit.finance.feature.auth.generated.resources.signed_out_title
import com.uit.finance.feature.auth.domain.model.SignInMode
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    val welcomeActionSurface = FinanceTheme.colors.welcomeActionSurface
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        FinanceTheme.colors.welcomeBackgroundTop,
                        FinanceTheme.colors.welcomeBackgroundMiddle,
                        FinanceTheme.colors.welcomeBackgroundBottom,
                    ),
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val waveTop = size.height * 0.60f
            val wave = Path().apply {
                moveTo(0f, waveTop + size.height * 0.025f)
                cubicTo(
                    size.width * 0.22f,
                    waveTop - size.height * 0.035f,
                    size.width * 0.68f,
                    waveTop + size.height * 0.025f,
                    size.width,
                    waveTop - size.height * 0.015f,
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path = wave, color = welcomeActionSurface)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(spacing.xxl))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FinanceLogo(Modifier.size(spacing.xl))
                Text(
                    text = stringResource(Res.string.signed_out_brand),
                    style = MaterialTheme.typography.titleLarge,
                    color = FinanceTheme.colors.welcomeText,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(spacing.lg))
            Text(
                text = stringResource(Res.string.signed_out_title),
                style = MaterialTheme.typography.headlineLarge,
                color = FinanceTheme.colors.welcomeText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = stringResource(Res.string.signed_out_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = FinanceTheme.colors.welcomeTextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(spacing.md))
            Image(
                painter = painterResource(Res.drawable.welcome_wallet),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .heightIn(min = spacing.xxl * 5, max = spacing.xxl * 8),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(spacing.md))

            state.error?.let { error ->
                ErrorBanner(message = error, onDismiss = { onIntent(SignedOutIntent.ErrorDismissed) })
                Spacer(Modifier.height(spacing.sm))
            }

            PrimaryButton(
                text = stringResource(Res.string.signed_out_create_account),
                onClick = { onIntent(SignedOutIntent.SignUpClicked) },
                enabled = !state.isBusy,
                loading = state.inProgress == SignInMode.SignUp,
                large = true,
            )
            Spacer(Modifier.height(spacing.md))
            SecondaryOutlinedButton(
                text = stringResource(Res.string.signed_out_sign_in),
                onClick = { onIntent(SignedOutIntent.SignInClicked) },
                enabled = !state.isBusy,
                large = true,
            )
            Spacer(Modifier.height(spacing.lg))
            Text(
                text = stringResource(Res.string.signed_out_security),
                style = MaterialTheme.typography.bodySmall,
                color = FinanceTheme.colors.welcomeTextSecondary,
            )
            Spacer(Modifier.height(spacing.md))
        }
    }

    if (state.otherAccountsPrompt) {
        ConfirmDialog(
            title = stringResource(Res.string.other_accounts_title),
            message = stringResource(Res.string.other_accounts_message),
            confirmLabel = stringResource(Res.string.other_accounts_delete),
            dismissLabel = stringResource(Res.string.other_accounts_keep),
            destructive = true,
            onConfirm = { onIntent(SignedOutIntent.WipeOtherAccounts) },
            onDismiss = { onIntent(SignedOutIntent.KeepOtherAccounts) },
        )
    }
}
