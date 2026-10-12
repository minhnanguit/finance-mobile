package com.uit.finance.feature.accounts.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.repository.AccountRepository

/** Lưu trữ = ẩn khỏi danh sách chọn và khỏi tổng, giữ nguyên lịch sử (D7). Bỏ lưu trữ là dùng lại bình thường. */
class SetAccountArchivedUseCase(private val accounts: AccountRepository) {
    suspend operator fun invoke(accountId: String, archived: Boolean): AppResult<Unit> = accounts.setArchived(accountId, archived)
}
