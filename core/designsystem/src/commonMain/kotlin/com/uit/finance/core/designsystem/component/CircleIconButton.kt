package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Nút tròn nền mờ chứa icon (chuông, cài đặt, menu thẻ). [badgeCount] > 0 thì hiện số đó ở góc (vd số điều kiện lọc). */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    badgeCount: Int = 0,
) {
    if (badgeCount <= 0) {
        CircleIconSurface(icon, contentDescription, onClick, modifier, size, containerColor, contentColor)
        return
    }
    BadgedBox(
        badge = { Badge { Text(badgeCount.toString()) } },
        modifier = modifier,
    ) {
        CircleIconSurface(icon, contentDescription, onClick, Modifier, size, containerColor, contentColor)
    }
}

@Composable
private fun CircleIconSurface(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    size: Dp,
    containerColor: Color,
    contentColor: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction, pressedScale = 0.9f).size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.5f))
        }
    }
}
