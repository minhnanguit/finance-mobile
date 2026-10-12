package com.uit.finance.core.designsystem.icon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class CategoryIconsTest {

    @Test
    fun `mỗi khoá cho chọn đều có icon và không hai khoá nào trùng hình`() {
        val icons = categoryIconChoices.map { key -> assertNotNull(categoryIcon(key), "thiếu icon cho '$key'") }
        assertEquals(icons.size, icons.toSet().size, "có hai lựa chọn trùng một icon")
    }

    @Test
    fun `khoá của danh mục mặc định backend đều có icon`() {
        val backendKeys = listOf(
            "restaurant", "directions_car", "shopping_bag", "receipt", "home", "favorite", "school", "movie",
            "family", "percent", "more_horiz", "payments", "redeem", "trending_up", "card_giftcard",
        )
        backendKeys.forEach { assertNotNull(categoryIcon(it), "thiếu icon cho '$it'") }
    }
}
