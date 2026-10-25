package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.ledger.domain.model.TransactionFilter
import com.uit.finance.core.ledger.domain.model.TransactionTotals
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

/** Dòng tổng của kết quả lọc (F10): đếm mọi dòng đang hiện, thu/chi chỉ cộng giao dịch đã xác nhận. */
class ObserveTransactionTotalsUseCase(private val transactions: TransactionRepository) {
    operator fun invoke(filter: TransactionFilter): Flow<TransactionTotals> = transactions.observeFilteredTotals(filter)
}
