package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.presentation.money.NumberSymbols

/**
 * Ô nhập số tiền cỡ lớn (màn thêm giao dịch, tạo ví). [value] là chuỗi đã làm sạch bằng `MoneyInput.sanitize`
 * (chỉ chữ số, dấu phần lẻ, dấu âm); dấu nhóm nghìn chỉ là hiệu ứng hiển thị nên con trỏ không bao giờ nhảy.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: String,
    label: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
    errorText: String? = null,
    amountColor: Color = LocalContentColor.current,
    style: TextStyle = MaterialTheme.typography.displaySmall,
) {
    val spacing = FinanceTheme.spacing
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style.copy(color = amountColor),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = GroupedAmountTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "$label, $currency" },
                decorationBox = { inner ->
                    if (value.isEmpty()) Text("0", style = style, color = placeholderColor)
                    inner()
                },
            )
            Text(
                text = currency,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = spacing.sm),
            )
        }
        if (errorText != null) {
            Text(errorText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** "1250000,5" → "1.250.000,5" khi hiển thị; bảng vị trí hai chiều giữ con trỏ đúng chỗ khi gõ/xoá. */
internal object GroupedAmountTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val wholeStart = if (raw.startsWith('-')) 1 else 0
        val wholeEnd = raw.indexOf(NumberSymbols.DECIMAL).takeIf { it >= 0 } ?: raw.length
        val wholeLength = wholeEnd - wholeStart

        val out = StringBuilder()
        val toTransformed = IntArray(raw.length + 1)
        val toOriginal = ArrayList<Int>(raw.length * 2)
        raw.forEachIndexed { index, char ->
            val wholeIndex = index - wholeStart
            if (index in wholeStart until wholeEnd && wholeIndex > 0 && (wholeLength - wholeIndex) % 3 == 0) {
                toOriginal += index // dấu nhóm đứng trước chữ số này
                out.append(NumberSymbols.GROUPING)
            }
            toTransformed[index] = out.length
            toOriginal += index
            out.append(char)
        }
        toTransformed[raw.length] = out.length
        toOriginal += raw.length

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toTransformed[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int = toOriginal[offset.coerceIn(0, out.length)]
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}
