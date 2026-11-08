package com.uit.finance.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

/**
 * Luật dữ liệu ngân sách (docs/BUDGET-PLAN.md Phase 4, ADR-007 §8). Bảng `budget` nằm chung `FinanceDatabase`
 * với outbox để bản ghi và op được ghi trong **một** transaction, nên phải chặn bằng luật thay vì tách DB.
 * Luật log số tiền của `LedgerDataRulesTest` đã phủ cả `amountMinor` của ngân sách.
 */
class BudgetDataRulesTest {

    /** Chính các luật này chứa những chuỗi bị cấm. */
    private val ARCHITECTURE_PACKAGE = "$ROOT.architecture"

    private val budgetRow = "$ROOT.core.database.Budget"

    @Test
    fun `only core-budget touches the budget table`() {
        productionScope.files
            .filter { !it.packageName.startsWith("$ROOT.core.budget") && !it.packageName.startsWith(ARCHITECTURE_PACKAGE) }
            .assertFalse { file -> file.text.contains("budgetQueries") || file.hasImport { it.name == budgetRow } }
    }
}
