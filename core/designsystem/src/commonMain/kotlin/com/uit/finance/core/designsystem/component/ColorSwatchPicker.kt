package com.uit.finance.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceMotion
import com.uit.finance.core.designsystem.theme.FinanceTheme

/** Hàng chấm màu để chọn (màu danh mục). Màu đang chọn có vòng ngoài và dấu tích. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatchPicker(
    colors: List<Pair<String, Color>>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        colors.forEachIndexed { index, (key, color) ->
            val selected = key == selectedKey
            val ring by animateDpAsState(if (selected) 3.dp else 0.dp, FinanceMotion.quick(), label = "swatchRing")
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(ring, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .padding(ring + 2.dp)
                    .clip(CircleShape)
                    .background(color)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(key) }
                    .semantics { contentDescription = "Màu ${index + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(FinanceIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
