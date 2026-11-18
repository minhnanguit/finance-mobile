package com.uit.finance.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

/**
 * Luật dữ liệu khoản định kỳ (RECURRING-PLAN.md Phase 5, ADR-009 §8). Bảng `recurring*` nằm chung `FinanceDatabase`
 * với outbox và ledger để khoản, op và giao dịch của kỳ được ghi trong **một** transaction (RS13), nên phải chặn bằng
 * luật. Giao dịch của kỳ đi qua `LedgerOccurrenceWriter` của core/ledger, không qua query ledger.
 */
class RecurringDataRulesTest {

    /** Chính các luật này chứa những chuỗi bị cấm. */
    private val ARCHITECTURE_PACKAGE = "$ROOT.architecture"

    private val recurringRows = listOf("Recurring", "Recurring_revision", "Recurring_occurrence_local")
        .map { "$ROOT.core.database.$it" }

    @Test
    fun `only core-recurring touches the recurring tables`() {
        productionScope.files
            .filter { !it.packageName.startsWith("$ROOT.core.recurring") && !it.packageName.startsWith(ARCHITECTURE_PACKAGE) }
            .assertFalse { file ->
                file.text.contains("recurringQueries") || file.hasImport { it.name in recurringRows }
            }
    }

    // R15: thông báo nhắc hiện trên màn hình khoá, không được mang số tiền (tinh thần ADR-006 B8).
    @Test
    fun `reminder notifications never carry amounts`() {
        productionScope.files
            .filter { it.packageName.startsWith("$ROOT.core.recurring") && it.text.contains("ReminderNotification(") }
            .assertFalse { file -> Regex("""ReminderNotification\([^)]*amount""", RegexOption.IGNORE_CASE).containsMatchIn(file.text) }
    }
}
