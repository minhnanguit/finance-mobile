package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme

/** Một lựa chọn trong [OptionSheet]. [key] để LazyColumn giữ đúng vị trí khi danh sách đổi. */
data class SheetOption<T>(
    val key: String,
    val value: T,
    val title: String,
    val subtitle: String? = null,
)

/**
 * Bảng chọn trượt từ dưới lên (chọn ví, tiền tệ). Chạm một dòng là chọn và đóng. [leading] vẽ icon đầu dòng
 * cho từng lựa chọn; dòng đang chọn có dấu tích.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionSheet(
    title: String,
    options: List<SheetOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    leading: (@Composable (T) -> Unit)? = null,
) {
    val spacing = FinanceTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            items(options, key = { it.key }) { option ->
                FinanceListItem(
                    title = option.title,
                    subtitle = option.subtitle,
                    leading = leading?.let { draw -> { draw(option.value) } },
                    trailing = if (option.value == selected) {
                        { Icon(FinanceIcons.Check, contentDescription = "Đang chọn", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}
