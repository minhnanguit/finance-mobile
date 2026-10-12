package com.uit.finance.feature.auth.presentation.localdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SecondaryTextButton
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.feature.auth.domain.model.LocalDataStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun LocalDataGateRoute(
    onReady: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: LocalDataGateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LocalDataGateEffect.Ready -> onReady()
                LocalDataGateEffect.OpenProfile -> onOpenProfile()
            }
        }
    }
    LocalDataGateScreen(state = state, onIntent = viewModel::onIntent)
}

/** Lần đầu đăng nhập mà mất mạng (S6), hoặc không mở được sổ: giải thích ngắn + thử lại. */
@Composable
internal fun LocalDataGateScreen(
    state: LocalDataGateState,
    onIntent: (LocalDataGateIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    if (state.status == LocalDataStatus.Preparing && !state.isRetrying) {
        LoadingIndicator(modifier)
        return
    }
    val needsNetwork = state.status == LocalDataStatus.NeedsNetwork
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = if (needsNetwork) FinanceIcons.Offline else FinanceIcons.Shield,
            title = if (needsNetwork) "Cần kết nối mạng lần đầu" else "Chưa mở được sổ của bạn",
            message = if (needsNetwork) {
                "Lần đầu đăng nhập, ứng dụng cần mạng để tải sổ thu chi của bạn. Sau đó bạn dùng được cả khi không có mạng."
            } else {
                "Máy chủ hoặc bộ nhớ máy đang gặp sự cố. Dữ liệu chưa gửi vẫn được giữ nguyên; hãy thử lại sau ít phút."
            },
        )
        PrimaryButton(
            text = "Thử lại",
            onClick = { onIntent(LocalDataGateIntent.RetryClicked) },
            loading = state.isRetrying,
        )
        SecondaryTextButton(text = "Đăng xuất", onClick = { onIntent(LocalDataGateIntent.LogoutClicked) })
    }
}
