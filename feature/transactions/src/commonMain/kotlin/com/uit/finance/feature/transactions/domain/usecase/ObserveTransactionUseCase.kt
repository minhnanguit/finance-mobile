package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

/** Một giao dịch theo id; `null` khi đã bị xoá (kể cả trên máy khác). */
class ObserveTransactionUseCase(private val transactions: TransactionRepository) {
    operator fun invoke(transactionId: String): Flow<Transaction?> = transactions.observe(transactionId)
}
