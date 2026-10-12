package com.uit.finance.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

/**
 * Giữ "đổi giao diện = sửa một chỗ trong core/designsystem": feature không được đi vòng qua design system
 * (dùng thẳng thư viện icon, tự đặt mã màu).
 */
class DesignSystemRulesTest {

    @Test
    fun `only core-designsystem imports the icon library - everything else uses FinanceIcons`() {
        productionScope.files
            .filter { !it.packageName.startsWith("$ROOT.core.designsystem") }
            .assertFalse { file -> file.hasImport { it.name.startsWith("com.composables.icons") } }
    }

    @Test
    fun `features never hardcode colours - they read MaterialTheme and FinanceTheme`() {
        val literalColor = Regex("""\bColor\(\s*0x""")
        productionScope.files
            .filter { it.packageName.startsWith(FEATURE_PREFIX) }
            .assertFalse { file -> literalColor.containsMatchIn(file.text) }
    }
}
