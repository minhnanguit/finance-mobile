package com.uit.finance.core.designsystem.component

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class GroupedAmountTransformationTest {

    private fun transform(raw: String) = GroupedAmountTransformation.filter(AnnotatedString(raw))

    @Test
    fun `nhóm nghìn phần nguyên và giữ nguyên phần lẻ`() {
        assertEquals("1.250.000", transform("1250000").text.text)
        assertEquals("1.250.000,5", transform("1250000,5").text.text)
        assertEquals("-12.000", transform("-12000").text.text)
        assertEquals("999", transform("999").text.text)
        assertEquals("", transform("").text.text)
    }

    @Test
    fun `con trỏ ánh xạ hai chiều bỏ qua dấu nhóm`() {
        val mapping = transform("1250000").offsetMapping // 1.250.000
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(2, mapping.originalToTransformed(1)) // con trỏ đứng sau dấu nhóm
        assertEquals(3, mapping.originalToTransformed(2))
        assertEquals(9, mapping.originalToTransformed(7))
        assertEquals(1, mapping.transformedToOriginal(2)) // ngay sau dấu nhóm đầu tiên
        assertEquals(7, mapping.transformedToOriginal(9))
    }
}
