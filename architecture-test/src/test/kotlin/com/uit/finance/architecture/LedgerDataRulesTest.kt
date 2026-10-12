package com.uit.finance.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

/**
 * Luật dữ liệu sổ thu chi (docs/LEDGER-PLAN.md Phase 4). Bảng ledger nằm chung `FinanceDatabase` với
 * outbox để entity và op được ghi trong **một** transaction, nên phải chặn bằng luật thay vì tách module.
 */
class LedgerDataRulesTest {

    /** Chính các luật này chứa những chuỗi bị cấm (IDE có thể chép chúng ra `bin/`). */
    private val ARCHITECTURE_PACKAGE = "$ROOT.architecture"

    private val ledgerQueries = listOf("accountQueries", "categoryQueries", "ledgerTransactionQueries")
    private val ledgerRows = listOf("Account", "Category", "Ledger_transaction", "SelectBalances")
        .map { "$ROOT.core.database.$it" }

    @Test
    fun `only core-ledger touches the ledger tables`() {
        productionScope.files
            .filter { !it.packageName.startsWith("$ROOT.core.ledger") && !it.packageName.startsWith(ARCHITECTURE_PACKAGE) }
            .assertFalse { file ->
                ledgerQueries.any { file.text.contains(it) } || file.hasImport { it.name in ledgerRows }
            }
    }

    // ADR-006 B8: log và Sentry không bao giờ được thấy số tiền, ghi chú, người nhận.
    @Test
    fun `logs never carry amounts, notes or payees`() {
        val logCall = Regex("""\b(logger|log|Logger|kermit)\.(v|d|i|w|e|a)\s*(\([^)]*\))?\s*\{[^}]*""")
        val sensitive = Regex("""\b(note|payee|amount\w*|openingBalance\w*)\b""")
        productionScope.files.filter { !it.packageName.startsWith(ARCHITECTURE_PACKAGE) }.assertFalse { file ->
            logCall.findAll(file.text).any { sensitive.containsMatchIn(it.value) } ||
                file.text.lines().any { it.contains("println(") && sensitive.containsMatchIn(it) }
        }
    }
}
