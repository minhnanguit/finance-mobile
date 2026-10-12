package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.icon.FinanceIcons

/** Ảnh đại diện tròn: hiện chữ cái đầu tên nếu có, không thì icon người dùng. */
@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    name: String? = null,
    size: Dp = 44.dp,
) {
    val initials = name?.initials()
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (initials.isNullOrBlank()) {
                Icon(FinanceIcons.Profile, contentDescription = null, modifier = Modifier.size(size * 0.5f))
            } else {
                Text(initials, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** "Nguyễn Văn An" → "NA": chữ đầu của từ đầu và từ cuối. */
private fun String.initials(): String {
    val words = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (words.size) {
        0 -> ""
        1 -> words.first().take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}
