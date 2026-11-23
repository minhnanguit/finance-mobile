package com.uit.finance.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

/**
 * Luật dữ liệu tự ghi từ thông báo ngân hàng (AUTO-CAPTURE-PLAN mục 3.7). Bảng `capture_*` giữ nguyên văn tin ngân
 * hàng (tối đa 14 ngày) nên chỉ `core/capture` được đọc/ghi; giao dịch nháp đi qua `LedgerOccurrenceWriter`.
 */
class CaptureDataRulesTest {

    /** Chính các luật này chứa những chuỗi bị cấm. */
    private val ARCHITECTURE_PACKAGE = "$ROOT.architecture"

    private val captureRows = listOf("$ROOT.core.database.Capture_event", "$ROOT.core.database.Capture_account_link")

    @Test
    fun `only core-capture touches the capture tables`() {
        productionScope.files
            .filter { !it.packageName.startsWith("$ROOT.core.capture") && !it.packageName.startsWith(ARCHITECTURE_PACKAGE) }
            .assertFalse { file -> file.text.contains("captureQueries") || file.hasImport { it.name in captureRows } }
    }
}
