package com.uit.finance.core.designsystem.component

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * Số tiền theo một kiểu cho cả app: dấu + mã tiền tệ nhỏ và mờ, số tiền đậm (`-VND 220.000`).
 * [amount] đã format sẵn bởi `MoneyFormatter`; màn hình không tự ghép chuỗi tiền.
 */
@Composable
fun AmountText(
    amount: String,
    currency: String,
    modifier: Modifier = Modifier,
    sign: String = "",
    amountColor: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    currencyStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val currencyColor = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = buildAnnotatedString {
            withStyle(currencyStyle.toSpanStyle().copy(color = currencyColor)) {
                append(sign)
                append(currency)
                append(' ')
            }
            withStyle(style.toSpanStyle().copy(color = amountColor)) {
                append(amount)
            }
        },
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
