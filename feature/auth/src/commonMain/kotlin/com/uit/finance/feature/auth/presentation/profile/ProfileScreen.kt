package com.uit.finance.feature.auth.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.theme.FinanceTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
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
            .verticalScroll(rememberScrollState())
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text(text = "Cá nhân", style = MaterialTheme.typography.headlineMedium)

        state.error?.let { error ->
            ErrorBanner(
                message = error,
                onRetry = {
                    onIntent(
                        when (state.errorAction) {
                            ProfileErrorAction.ReloadProfile -> ProfileIntent.Retry
                            ProfileErrorAction.RetryLogout -> ProfileIntent.Logout
                        },
                    )
                },
            )
        }

        state.profile?.let { profile ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(text = "Thông tin tài khoản", style = MaterialTheme.typography.titleMedium)
                    Text(text = profile.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(text = profile.email, style = MaterialTheme.typography.bodyLarge)
                    val joined = profile.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    Text(
                        text = "Tham gia từ ${joined.format(DisplayDate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { onIntent(ProfileIntent.Logout) },
            enabled = !state.isLoggingOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = spacing.xxl),
        ) {
            Text(if (state.isLoggingOut) "Đang đăng xuất..." else "Đăng xuất")
        }
    }

    state.unsentChangesWarning?.let { unsent ->
        ConfirmDialog(
            title = "Còn thay đổi chưa đồng bộ",
            message = "Có $unsent thay đổi chưa lên server. Đăng xuất bây giờ sẽ xoá sổ trên máy này và mất các thay đổi đó.",
            confirmLabel = "Vẫn đăng xuất",
            dismissLabel = "Ở lại",
            destructive = true,
            onConfirm = { onIntent(ProfileIntent.ConfirmLogout) },
            onDismiss = { onIntent(ProfileIntent.DismissLogoutWarning) },
        )
    }
}

/** dd/MM/yyyy. */
private val DisplayDate = LocalDate.Format {
    day()
    char('/')
    monthNumber()
    char('/')
    year()
}
