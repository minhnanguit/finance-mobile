package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.repository.TransactionRepository

/**
 * Xoá thật (tombstone + op DELETE). Không có "khôi phục": S4 cho xoá luôn thắng, nên UI hoàn tác bằng cách hoãn
 * gọi use case này chứ không xoá rồi ghi lại.
 */
class DeleteTransactionUseCase(private val transactions: TransactionRepository) {
    suspend operator fun invoke(transactionId: String): AppResult<Unit> = transactions.delete(transactionId)
}
