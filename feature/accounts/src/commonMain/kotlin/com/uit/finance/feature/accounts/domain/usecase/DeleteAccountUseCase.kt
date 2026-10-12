package com.uit.finance.feature.accounts.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.repository.AccountRepository

/** Chỉ xoá được ví chưa có giao dịch; có rồi thì repository trả `ledger.in_use` và UI gợi ý lưu trữ (D7). */
class DeleteAccountUseCase(private val accounts: AccountRepository) {
    suspend operator fun invoke(accountId: String): AppResult<Unit> = accounts.delete(accountId)
}
