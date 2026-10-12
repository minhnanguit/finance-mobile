package com.uit.finance.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme

/**
 * Ô chọn trong lưới (danh mục khi ghi giao dịch, icon khi tạo danh mục): [IconTile] + nhãn (bỏ trống được, khi đó
 * nên có [contentDescription] cho screen reader), viền đậm khi chọn.
 * Một chạm là chọn xong, nên lưới này là một trong ba chạm của luồng "ghi giao dịch".
 */
@Composable
fun ChoiceTile(
    icon: ImageVector,
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val spacing = FinanceTheme.spacing
    val shape = MaterialTheme.shapes.medium
    val borderColor by animateColorAsState(
        targetValue = if (selected) contentColor else Color.Transparent,
        animationSpec = FinanceMotion.quick(),
        label = "choiceBorder",
    )
    Column(
        modifier = modifier
            .clip(shape)
            .border(2.dp, borderColor, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription?.let { this.contentDescription = it } }
            .padding(vertical = spacing.sm, horizontal = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        IconTile(icon = icon, containerColor = containerColor, contentColor = contentColor, size = 44.dp)
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
