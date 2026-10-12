package com.uit.finance.feature.accounts.domain.usecase

import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Một ví theo id; `null` khi không còn (bị xoá trên máy khác). */
class ObserveAccountUseCase(private val accounts: AccountRepository) {
    operator fun invoke(accountId: String): Flow<Account?> =
        accounts.observeAccounts().map { list -> list.firstOrNull { it.id == accountId } }.distinctUntilChanged()
}
