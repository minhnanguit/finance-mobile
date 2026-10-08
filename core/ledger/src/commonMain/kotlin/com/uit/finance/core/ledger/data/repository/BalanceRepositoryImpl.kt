package com.uit.finance.core.ledger.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.ledger.data.local.LedgerStore
import com.uit.finance.core.ledger.domain.model.AccountBalance
import com.uit.finance.core.ledger.domain.model.BalanceSummary
import com.uit.finance.core.ledger.domain.repository.BalanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    /** Không cộng thẳng các tiền tệ khác nhau (ADR-001); bỏ ví đã archive. */
    private fun totalsByCurrency(accounts: List<AccountBalance>): List<Money> =
        accounts.filterNot { it.archived }
            .groupBy { it.balance.currency }
            .map { (currency, balances) -> balances.fold(Money.zero(currency)) { total, it -> total + it.balance } }
            .sortedBy { it.currency }
}
