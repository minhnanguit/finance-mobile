package com.uit.finance.core.ledger.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.domain.model.AccountBalance
import com.uit.finance.core.ledger.domain.model.BalanceSummary
import com.uit.finance.core.ledger.domain.model.CashFlow
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** Một câu SUM cho mọi ví (Account.sq `selectBalances`), cùng công thức với backend (ADR-005 §5). */
internal class BalanceRepositoryImpl(
    private val store: LedgerStore,
    private val dispatchers: DispatcherProvider,
) : BalanceRepository {

    override fun observeBalances(): Flow<BalanceSummary> = store.observe(BalanceSummary.EMPTY) { database ->
        database.accountQueries.selectBalances().asFlow().mapToList(dispatchers.io).map { rows ->
            val accounts = rows.map { row ->
                AccountBalance(
                    accountId = row.id,
                    balance = Money(row.balance_minor, row.currency),
                    archived = row.archived != 0L,
                )
            }
            BalanceSummary(accounts = accounts, totals = totalsByCurrency(accounts))
        }
    }

    override fun observeCashFlow(from: LocalDate, to: LocalDate): Flow<CashFlow> = store.observe(CashFlow.EMPTY) { database ->
        database.ledgerTransactionQueries.selectCashFlowBetween(from.toString(), to.toString())
            .asFlow().mapToList(dispatchers.io)
            .map { rows ->
                val byType = rows.groupBy({ TransactionType.valueOf(it.type) }, { Money(it.total_minor, it.currency) })
                CashFlow(
                    income = byType[TransactionType.INCOME].orEmpty(),
                    expense = byType[TransactionType.EXPENSE].orEmpty(),
                )
            }
    }

    /**
     * Không cộng thẳng các tiền tệ khác nhau (ADR-001); bỏ ví đã archive. Tổng vượt Long thì kẹt ở biên giống
     * câu SQL số dư, thay vì `Money.plus` ném ArithmeticException làm chết cả Flow.
     */
    private fun totalsByCurrency(accounts: List<AccountBalance>): List<Money> =
        accounts.filterNot { it.archived }
            .groupBy { it.balance.currency }
            .map { (currency, balances) -> Money(balances.fold(0L) { total, it -> total.saturatingPlus(it.balance.amountMinor) }, currency) }
            .sortedBy { it.currency }

    private fun Long.saturatingPlus(other: Long): Long {
        val sum = this + other
        return when {
            this > 0 && other > 0 && sum < 0 -> Long.MAX_VALUE
            this < 0 && other < 0 && sum >= 0 -> Long.MIN_VALUE
            else -> sum
        }
    }
}
