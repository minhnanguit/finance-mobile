package com.uit.finance.core.presentation.money

/**
 * Ký hiệu số của app (quy ước Việt Nam): nhóm nghìn ".", phần lẻ ",". Hiển thị ([MoneyFormatter]), ô nhập
 * ([MoneyInput]) và hiệu ứng nhóm nghìn khi gõ đều đọc từ đây, nên đổi quy ước chỉ sửa một chỗ.
 */
object NumberSymbols {
    const val GROUPING: Char = '.'
    const val DECIMAL: Char = ','
}
