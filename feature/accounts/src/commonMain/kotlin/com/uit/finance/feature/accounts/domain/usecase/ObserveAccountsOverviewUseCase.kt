package com.uit.finance.feature.accounts.domain.usecase

import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.BalanceRepository
import com.uit.finance.feature.accounts.domain.model.AccountWithBalance
import com.uit.finance.feature.accounts.domain.model.AccountsOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Ví + số dư + tổng theo tiền tệ; tự cập nhật khi có giao dịch mới hay sync về. */
class ObserveAccountsOverviewUseCase(
    private val accounts: AccountRepository,
    private val balances: BalanceRepository,
) {
    operator fun invoke(): Flow<AccountsOverview> =
        combine(accounts.observeAccounts(), balances.observeBalances()) { accountList, summary ->
            val balanceById = summary.accounts.associate { it.accountId to it.balance }
            val (archived, active) = accountList
                .map { AccountWithBalance(it, balanceById[it.id] ?: it.openingBalance) }
                .partition { it.account.archived }
            AccountsOverview(active = active, archived = archived, totals = summary.totals)
        }
}
