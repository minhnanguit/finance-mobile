package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.uit.finance.core.designsystem.theme.FinanceTheme

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    large: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(if (large) 56.dp else 52.dp)
            .loadingSemantics(loading)
            .then(
                if (large) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = FinanceTheme.colors.brandDeep.copy(alpha = 0.28f),
                        spotColor = FinanceTheme.colors.brandDeep.copy(alpha = 0.34f),
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (large) {
                    Modifier
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    FinanceTheme.colors.welcomeGradientStart,
                                    FinanceTheme.colors.welcomeGradientEnd,
                                ),
                            ),
                        )
                } else {
                    Modifier
                },
            ),
        shape = if (large) CircleShape else RoundedCornerShape(12.dp),
        border = if (large) {
            BorderStroke(1.dp, FinanceTheme.colors.logoAqua.copy(alpha = 0.72f))
        } else {
            null
        },
        elevation = if (large) {
            ButtonDefaults.buttonElevation(
                defaultElevation = 3.dp,
                pressedElevation = 1.dp,
                focusedElevation = 4.dp,
                hoveredElevation = 5.dp,
                disabledElevation = 0.dp,
            )
        } else {
            ButtonDefaults.buttonElevation()
        },
        colors = if (large) {
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = FinanceTheme.colors.onBrand,
                disabledContainerColor = FinanceTheme.colors.welcomeOutline,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        ButtonLabel(
            text = text,
            loading = loading,
            indicatorColor = if (large) FinanceTheme.colors.onBrand else MaterialTheme.colorScheme.onPrimary,
            textStyle = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
        )
    }
}

/** Nút viền của màn chào: [large] là pill trắng, còn lại là outlined mặc định. */
@Composable
fun SecondaryOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    large: Boolean = false,
) {
    val content = if (large) FinanceTheme.colors.brandDeep else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(if (large) 56.dp else 52.dp)
            .loadingSemantics(loading)
            .then(
                if (large) {
                    Modifier.shadow(
                        elevation = 7.dp,
                        shape = CircleShape,
                        ambientColor = FinanceTheme.colors.brandDeep.copy(alpha = 0.14f),
                        spotColor = FinanceTheme.colors.brandDeep.copy(alpha = 0.18f),
                    )
                } else {
                    Modifier
                },
            ),
        shape = if (large) CircleShape else RoundedCornerShape(12.dp),
        border = if (large) BorderStroke(2.dp, FinanceTheme.colors.welcomeOutline) else null,
        colors = if (large) {
            ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = FinanceTheme.colors.brandDeep,
            )
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        elevation = if (large) {
            ButtonDefaults.buttonElevation(
                defaultElevation = 2.dp,
                pressedElevation = 0.dp,
                focusedElevation = 3.dp,
                hoveredElevation = 4.dp,
                disabledElevation = 0.dp,
            )
        } else {
            ButtonDefaults.buttonElevation()
        },
    ) {
        ButtonLabel(
            text = text,
            loading = loading,
            indicatorColor = content,
            textStyle = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
        )
    }
}

/** [muted]: chữ màu phụ, cho hành động ít dùng đặt xa nút chính (vd "Xoá dữ liệu trên máy này" ở chân màn). */
@Composable
fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    muted: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = if (muted) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ButtonDefaults.textButtonColors()
        },
    ) {
        Text(text = text)
    }
}

/** Nút xác nhận việc không hoàn tác được (xoá tài khoản): nền màu lỗi, cùng kích thước [PrimaryButton]. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.fillMaxWidth().height(52.dp).loadingSemantics(loading),
    ) {
        ButtonLabel(text = text, loading = loading, indicatorColor = MaterialTheme.colorScheme.onError)
    }
}

/**
 * Nút phụ viền mảnh, ngang hàng [PrimaryButton] (đăng xuất, huỷ). [destructive] tô chữ và viền màu lỗi.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
) {
    val content = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        border = BorderStroke(1.dp, if (destructive) content else MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        modifier = modifier.fillMaxWidth().height(52.dp).loadingSemantics(loading),
    ) {
        ButtonLabel(text = text, loading = loading, indicatorColor = content, icon = icon)
    }
}

/**
 * Chữ của nút vẫn nằm đó (trong suốt) khi đang xử lý: nút không đổi kích thước, và trình đọc màn hình vẫn đọc được tên
 * nút thay vì một vòng xoay không tên.
 */
@Composable
private fun ButtonLabel(
    text: String,
    loading: Boolean,
    indicatorColor: Color,
    icon: ImageVector? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Box(contentAlignment = Alignment.Center) {
        Row(Modifier.alpha(if (loading) 0f else 1f), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(FinanceTheme.spacing.sm))
            }
            Text(text = text, style = textStyle)
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = indicatorColor)
        }
    }
}

private fun Modifier.loadingSemantics(loading: Boolean): Modifier =
    if (loading) semantics { stateDescription = LOADING_STATE } else this

private const val LOADING_STATE = "Đang xử lý"
