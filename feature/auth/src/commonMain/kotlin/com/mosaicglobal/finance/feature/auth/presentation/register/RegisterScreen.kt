package com.mosaicglobal.finance.feature.auth.presentation.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosaicglobal.finance.core.designsystem.component.ErrorBanner
import com.mosaicglobal.finance.core.designsystem.component.FinanceTextField
import com.mosaicglobal.finance.core.designsystem.component.PrimaryButton
import com.mosaicglobal.finance.core.designsystem.component.SecondaryTextButton
import com.mosaicglobal.finance.core.designsystem.theme.FinanceTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun RegisterRoute(
    onNavigateToLogin: () -> Unit,
    onRegistered: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RegisterEffect.NavigateToHome -> onRegistered()
                RegisterEffect.NavigateToLogin -> onNavigateToLogin()
            }
        }
    }

    RegisterScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun RegisterScreen(
    state: RegisterState,
    onIntent: (RegisterIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Create your account", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(spacing.xl))

        state.error?.let { error ->
            ErrorBanner(message = error, onDismiss = { onIntent(RegisterIntent.ErrorDismissed) })
            Spacer(Modifier.height(spacing.md))
        }

        FinanceTextField(
            value = state.displayName,
            onValueChange = { onIntent(RegisterIntent.DisplayNameChanged(it)) },
            label = "Display name",
            errorText = state.displayNameError?.resolve(),
            enabled = !state.isSubmitting,
        )
        Spacer(Modifier.height(spacing.md))
        FinanceTextField(
            value = state.email,
            onValueChange = { onIntent(RegisterIntent.EmailChanged(it)) },
            label = "Email",
            errorText = state.emailError?.resolve(),
            enabled = !state.isSubmitting,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(spacing.md))
        FinanceTextField(
            value = state.password,
            onValueChange = { onIntent(RegisterIntent.PasswordChanged(it)) },
            label = "Password (min. 8 characters)",
            errorText = state.passwordError?.resolve(),
            enabled = !state.isSubmitting,
            isPassword = true,
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(spacing.lg))

        PrimaryButton(
            text = "Create account",
            onClick = { onIntent(RegisterIntent.Submit) },
            enabled = state.canSubmit,
            loading = state.isSubmitting,
        )
        Spacer(Modifier.height(spacing.sm))
        SecondaryTextButton(
            text = "I already have an account",
            onClick = { onIntent(RegisterIntent.LoginClicked) },
            enabled = !state.isSubmitting,
        )
    }
}
