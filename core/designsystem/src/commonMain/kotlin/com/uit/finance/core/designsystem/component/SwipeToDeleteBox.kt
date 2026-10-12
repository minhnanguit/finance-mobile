package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme

/**
 * Vuốt từ phải sang trái để xoá: nền đỏ + icon thùng rác lộ ra dưới dòng. [onDelete] chỉ báo ý định; nơi gọi
 * quyết định xoá thật hay xoá trễ để còn hoàn tác.
 */
@Composable
fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = FinanceTheme.spacing.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(FinanceIcons.Delete, contentDescription = "Xoá", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}
