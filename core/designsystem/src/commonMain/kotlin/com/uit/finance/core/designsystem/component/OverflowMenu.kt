package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons

/** Một mục trong [OverflowMenu]. [destructive] tô màu lỗi (xoá). */
data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Nút "⋮" mở menu thao tác cho một dòng (sửa, lưu trữ, đổi thứ tự, xoá). Không có mục nào thì ẩn. */
@Composable
fun OverflowMenu(
    actions: List<MenuAction>,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        CircleIconButton(
            icon = FinanceIcons.MoreVertical,
            contentDescription = contentDescription,
            onClick = { expanded = true },
            size = 36.dp,
            containerColor = Color.Transparent,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(
                    text = { Text(action.label, color = color) },
                    leadingIcon = { Icon(action.icon, contentDescription = null, tint = color) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
