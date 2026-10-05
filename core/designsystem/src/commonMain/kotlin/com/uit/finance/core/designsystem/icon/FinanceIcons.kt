package com.uit.finance.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Bộ icon nét (line icon) dùng chung, dạng [ImageVector] để đi qua `Icon()` của Material:
 * tự nhận tint từ `LocalContentColor`, kích thước chuẩn 24dp, có `contentDescription`.
 * Thêm icon = thêm một property, không sửa icon cũ.
 */
object FinanceIcons {
    val Home: ImageVector by lazy {
        lineIcon("Home") {
            moveTo(3.4f, 11f); lineTo(12f, 3.8f); lineTo(20.6f, 11f)
            moveTo(5.5f, 10.3f); verticalLineTo(20.2f); horizontalLineTo(18.5f); verticalLineTo(10.3f)
            moveTo(10.3f, 20.2f); verticalLineTo(14.6f); horizontalLineTo(13.7f); verticalLineTo(20.2f)
        }
    }

    val Transactions: ImageVector by lazy {
        lineIcon("Transactions") {
            moveTo(7.7f, 4.3f); verticalLineTo(19.4f)
            moveTo(3.6f, 8.6f); lineTo(7.7f, 4.3f); lineTo(11.8f, 8.6f)
            moveTo(16.3f, 19.7f); verticalLineTo(4.6f)
            moveTo(12.2f, 15.4f); lineTo(16.3f, 19.7f); lineTo(20.4f, 15.4f)
        }
    }

    val Budget: ImageVector by lazy {
        lineIcon("Budget") {
            moveTo(5.8f, 4.3f); horizontalLineTo(18.2f)
            arcTo(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20.6f, 6.7f)
            verticalLineTo(17.3f)
            arcTo(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.2f, 19.7f)
            horizontalLineTo(5.8f)
            arcTo(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3.4f, 17.3f)
            verticalLineTo(6.7f)
            arcTo(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.8f, 4.3f)
            close()
            moveTo(6.7f, 15.4f); verticalLineTo(12.2f)
            moveTo(12f, 15.4f); verticalLineTo(8.6f)
            moveTo(17.3f, 15.4f); verticalLineTo(10.6f)
        }
    }

    val Profile: ImageVector by lazy {
        lineIcon("Profile") {
            moveTo(8.4f, 7.9f)
            arcTo(3.6f, 3.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 15.6f, 7.9f)
            arcTo(3.6f, 3.6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.4f, 7.9f)
            moveTo(4.8f, 20.2f)
            quadTo(5.5f, 15.1f, 12f, 15.1f)
            quadTo(18.5f, 15.1f, 19.2f, 20.2f)
        }
    }

    val Add: ImageVector by lazy {
        lineIcon("Add") {
            moveTo(12f, 4.3f); verticalLineTo(19.7f)
            moveTo(4.3f, 12f); horizontalLineTo(19.7f)
        }
    }

    val Reports: ImageVector by lazy {
        lineIcon("Reports") {
            moveTo(3.8f, 19.7f); horizontalLineTo(20.2f)
            moveTo(6.7f, 17.3f); verticalLineTo(13.2f)
            moveTo(12f, 17.3f); verticalLineTo(5.8f)
            moveTo(17.3f, 17.3f); verticalLineTo(10.1f)
        }
    }

    val ChevronLeft: ImageVector by lazy {
        lineIcon("ChevronLeft") { moveTo(14.6f, 5.3f); lineTo(8.4f, 12f); lineTo(14.6f, 18.7f) }
    }

    val ChevronRight: ImageVector by lazy {
        lineIcon("ChevronRight") { moveTo(9.4f, 5.3f); lineTo(15.6f, 12f); lineTo(9.4f, 18.7f) }
    }
}

/** Icon nét trên lưới 24×24. Màu nét chỉ là giá trị mặc định, `Icon()` sẽ tint lại. */
private fun lineIcon(name: String, pathData: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = "FinanceIcons.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathData,
    ).build()
