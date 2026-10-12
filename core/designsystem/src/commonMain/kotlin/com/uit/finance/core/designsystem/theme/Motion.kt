package com.uit.finance.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Một chỗ chỉnh mọi chuyển động của app. Màn hình dùng spec ở đây thay vì tự đặt duration/easing,
 * để toàn app chuyển động cùng một nhịp.
 */
object FinanceMotion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 300
    const val LONG_MS = 450

    /** Material 3 "emphasized decelerate": vào nhanh, dừng mềm. */
    val decelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> quick(): FiniteAnimationSpec<T> = tween(SHORT_MS, easing = standardEasing)
    fun <T> standard(): FiniteAnimationSpec<T> = tween(MEDIUM_MS, easing = standardEasing)
    fun <T> emphasized(): FiniteAnimationSpec<T> = tween(LONG_MS, easing = decelerateEasing)

    /** Nảy nhẹ cho phản hồi khi chạm (nút, thẻ). */
    fun <T> gentleSpring(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
}
