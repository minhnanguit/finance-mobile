package com.uit.finance.feature.reports.testing

import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeBalanceRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.FakeSpendingRepository
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.ledger.testing.transaction
import com.uit.finance.feature.reports.domain.usecase.ObserveCategoryBreakdownUseCase
import com.uit.finance.feature.reports.domain.usecase.ObserveReportOverviewUseCase
import kotlinx.datetime.LocalDate

/** Một bộ fake dùng chung: giao dịch và danh mục ghi một lần, mọi repository đọc cùng nguồn. */
class ReportFixture {
    val spending = FakeSpendingRepository()
    val balances = FakeBalanceRepository(spending.transactions)
    val categories = FakeCategoryRepository()
    val accounts = FakeAccountRepository().also { it.accounts.value = listOf(account("wallet")) }

    private var nextId = 0

    val overview = ObserveReportOverviewUseCase(spending, balances, categories, accounts)
    val breakdown = ObserveCategoryBreakdownUseCase(spending, categories, accounts)

    fun categories(vararg list: Category) {
        categories.categories.value = list.toList()
        spending.categories.value = list.toList()
    }

    fun spend(categoryId: String?, amount: Long, on: LocalDate, currency: String = "VND", accountId: String = "wallet") =
        add(TransactionType.EXPENSE, categoryId, amount, on, currency, accountId)

    fun earn(categoryId: String?, amount: Long, on: LocalDate, currency: String = "VND") =
        add(TransactionType.INCOME, categoryId, amount, on, currency, "wallet")

    private fun add(type: TransactionType, categoryId: String?, amount: Long, on: LocalDate, currency: String, accountId: String) {
        val tx: Transaction = transaction(
            id = "t${++nextId}",
            type = type,
            accountId = accountId,
            amountMinor = amount,
            occurredOn = on,
            currency = currency,
            categoryId = categoryId,
        )
        spending.transactions.value += tx
    }
}

fun expenseCategory(id: String, parentId: String? = null, color: String? = null) =
    category(id = id, name = id.replaceFirstChar { it.uppercase() }, color = color).copy(parentId = parentId)

fun incomeCategory(id: String) = category(id = id, name = id, kind = CategoryKind.INCOME)
