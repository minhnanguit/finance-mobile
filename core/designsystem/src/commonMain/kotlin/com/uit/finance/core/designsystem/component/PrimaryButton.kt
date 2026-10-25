package com.uit.finance.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = text,
                style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text = text)
    }
}

@Composable
fun SecondaryOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(if (large) 56.dp else 52.dp)
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
        Text(
            text = text,
            style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
        )
    }
}
