package com.uit.finance.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Hiện thông báo một lần đến từ state của màn hình (MVI: thông báo nằm trong state, không phải effect, để không
 * mất khi xoay màn hình). Hiện xong hoặc bị vuốt đi thì gọi [onShown]; có [actionLabel] thì bấm nút gọi [onAction].
 */
@Composable
fun MessageSnackbarHost(
    message: String?,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    duration: SnackbarDuration = SnackbarDuration.Short,
) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val result = hostState.showSnackbar(message = message, actionLabel = actionLabel, duration = duration)
        if (result == SnackbarResult.ActionPerformed) onAction()
        onShown()
    }
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.secondaryContainer,
        )
    }
}
