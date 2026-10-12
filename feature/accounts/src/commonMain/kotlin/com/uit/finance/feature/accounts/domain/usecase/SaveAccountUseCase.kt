package com.uit.finance.feature.accounts.domain.usecase

import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.asFailure
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.feature.accounts.domain.model.AccountInput
import kotlinx.coroutines.flow.first

/**
 * Tạo hoặc sửa ví. Ví mới đứng cuối danh sách (`sortOrder` lớn nhất + 1). Sửa thì giữ trạng thái lưu trữ và vị
 * trí; đổi tiền tệ của ví đã có giao dịch bị repository chặn (`ledger.currency_locked`, D8).
 */
class SaveAccountUseCase(private val accounts: AccountRepository) {
    suspend operator fun invoke(input: AccountInput): AppResult<Account> {
        val existing = accounts.observeAccounts().first()
        if (input.id == null) {
            val nextOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            return accounts.create(AccountDraft(input.name, input.type, input.currency, input.openingBalanceMinor, nextOrder))
        }
        val current = existing.firstOrNull { it.id == input.id }
            ?: return AppError.Validation("account", LedgerErrorCode.NOT_FOUND).asFailure()
        return accounts.update(
            current.copy(name = input.name, type = input.type, openingBalance = Money(input.openingBalanceMinor, input.currency)),
        )
    }
}
