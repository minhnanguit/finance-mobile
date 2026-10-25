package com.uit.finance.feature.transactions.presentation

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.TransactionTotals
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.feature.transactions.presentation.history.CurrencyTotalsUi
import com.uit.finance.feature.transactions.presentation.history.toCategoryFilterOptions
import com.uit.finance.feature.transactions.presentation.history.toUi
import kotlin.test.Test
import kotlin.test.assertEquals

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class HistoryFilterUiTest {

    @Test
    fun `danh mục xếp theo tên bỏ dấu - chi trước thu - con ngay sau cha`() {
        val categories = listOf(
            category("health", "Sức khoẻ"),
            category("salary", "Lương", kind = CategoryKind.INCOME),
            category("food", "Ăn uống"),
            category("coffee", "Cà phê").copy(parentId = "food"),
            category("bbq", "Ăn vặt").copy(parentId = "food"),
            category("move", "Di chuyển"),
        )

        assertEquals(
            listOf("Ăn uống", "Ăn vặt", "Cà phê", "Di chuyển", "Sức khoẻ", "Lương"),
            categories.toCategoryFilterOptions().map { it.name },
        )
    }

    @Test
    fun `dòng tổng ẩn vế bằng 0`() {
        val totals = TransactionTotals(
            count = 2,
            draftCount = 0,
            cashFlow = CashFlow(income = emptyList(), expense = listOf(Money(45_000, "VND"))),
        )

        val ui = totals.toUi(MoneyFormatter())

        assertEquals("2 giao dịch", ui.summary)
        assertEquals(listOf(CurrencyTotalsUi("VND", income = null, expense = "45.000")), ui.currencies)
    }
}
