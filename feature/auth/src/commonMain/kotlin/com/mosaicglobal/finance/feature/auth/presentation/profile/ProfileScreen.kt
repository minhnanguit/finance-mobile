package com.mosaicglobal.finance.feature.auth.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mosaicglobal.finance.core.designsystem.component.ErrorBanner
import com.mosaicglobal.finance.core.designsystem.component.LoadingIndicator
import com.mosaicglobal.finance.core.designsystem.component.PrimaryButton
import com.mosaicglobal.finance.core.designsystem.theme.FinanceTheme
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ProfileRoute(
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ProfileEffect.LoggedOut -> onLoggedOut()
            }
        }
    }

    ProfileScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun ProfileScreen(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    if (state.isLoading) {
        LoadingIndicator(modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(spacing.lg),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(text = "Profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(spacing.lg))

        state.error?.let { error ->
            ErrorBanner(message = error, onRetry = { onIntent(ProfileIntent.Retry) })
            Spacer(Modifier.height(spacing.md))
        }

        state.profile?.let { profile ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(text = profile.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(text = profile.email, style = MaterialTheme.typography.bodyLarge)
                    val joined = profile.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    Text(
                        text = "Member since $joined",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "ID ${profile.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = "Sign out",
            onClick = { onIntent(ProfileIntent.Logout) },
            loading = state.isLoggingOut,
        )
    }
}
