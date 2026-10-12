package com.uit.finance.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Màu user chọn cho danh mục (`Category.color`, chuỗi `#RRGGBB` lưu và sync lên server). Bảng gồm đúng các màu
 * danh mục mặc định của backend (`DefaultCategoryTemplate`) để danh mục mới hợp tông với danh mục có sẵn.
 */
object CategoryPalette {
    val hexes: List<String> = listOf(
        "#F97316", "#EAB308", "#84CC16", "#22C55E", "#10B981", "#14B8A6",
        "#06B6D4", "#3B82F6", "#8B5CF6", "#A855F7", "#EC4899", "#F43F5E",
        "#EF4444", "#64748B", "#6B7280", "#9CA3AF",
    )

    /** Màu mặc định khi danh mục chưa có màu. */
    val DEFAULT_HEX: String = hexes.first()

    /** "#F97316" → Color; sai định dạng trả `null` để nơi gọi dùng màu theo chiều tiền. */
    fun color(hex: String?): Color? {
        val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
        val rgb = digits.toLongOrNull(16) ?: return null
        return Color(0xFF000000 or rgb)
    }

    /** Nền mờ cho ô icon có màu danh mục: đủ nhạt để icon cùng màu vẫn nổi ở cả nền sáng lẫn tối. */
    fun container(color: Color): Color = color.copy(alpha = 0.16f)
}
