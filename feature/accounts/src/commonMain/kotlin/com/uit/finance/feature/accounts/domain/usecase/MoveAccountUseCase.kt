package com.uit.finance.feature.accounts.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.feature.accounts.domain.model.MoveDirection
import kotlinx.coroutines.flow.first

/**
 * Đổi chỗ một ví đang dùng với ví kề trên/dưới. Ghi lại thứ tự của mọi ví (đang dùng trước, đã lưu trữ sau) trong
 * một lần; repository chỉ sinh op sync cho ví thật sự đổi chỗ. Đã ở đầu/cuối thì không làm gì.
 */
class MoveAccountUseCase(private val accounts: AccountRepository) {
    suspend operator fun invoke(accountId: String, direction: MoveDirection): AppResult<Unit> {
        val all = accounts.observeAccounts().first()
        val active = all.filterNot { it.archived }.toMutableList()
        val from = active.indexOfFirst { it.id == accountId }
        val to = if (direction == MoveDirection.Up) from - 1 else from + 1
        if (from < 0 || to !in active.indices) return AppResult.Success(Unit)
        active.add(to, active.removeAt(from))
        return accounts.reorder(active.map { it.id } + all.filter { it.archived }.map { it.id })
    }
}
