package com.uit.finance.core.ledger.domain.repository

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.AccountDraft
import com.uit.finance.core.ledger.domain.model.BalanceSummary
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.model.CategoryDraft
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionDraft
import kotlinx.coroutines.flow.Flow

/*
 * Mọi lần ghi lưu vào DB của user trên máy rồi mới gửi lên server khi có mạng (offline-first, ADR-002):
 * thành công nghĩa là đã lưu ở máy, chưa chắc đã lên server. Vi phạm luật trả
 * `AppError.Validation` với mã trong `LedgerErrorCode`. Chưa mở sổ của user nào: `AppError.Storage`.
 * Mọi `observe*` tự đổi theo user đang đăng nhập.
 */

interface AccountRepository {
    /** Ví chưa xoá, kể cả đã archive, theo `sortOrder`. */
    fun observeAccounts(): Flow<List<Account>>
    suspend fun create(draft: AccountDraft): AppResult<Account>

    /** Đổi tiền tệ khi ví đã có giao dịch: `ledger.currency_locked` (D8). */
    suspend fun update(account: Account): AppResult<Account>
    suspend fun setArchived(accountId: String, archived: Boolean): AppResult<Unit>

    /** Ví đã có giao dịch chỉ archive được: `ledger.in_use` (D7). */
    suspend fun delete(accountId: String): AppResult<Unit>
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun create(draft: CategoryDraft): AppResult<Category>

    /** `kind` không đổi được: `ledger.kind_immutable`. */
    suspend fun update(category: Category): AppResult<Category>
    suspend fun setArchived(categoryId: String, archived: Boolean): AppResult<Unit>
    suspend fun delete(categoryId: String): AppResult<Unit>
}

interface TransactionRepository {
    /** Mới nhất trước: ngày giảm dần, rồi giờ, rồi id. */
    fun observeRecent(limit: Int): Flow<List<Transaction>>
    fun observe(transactionId: String): Flow<Transaction?>
    suspend fun record(draft: TransactionDraft): AppResult<Transaction>
    suspend fun update(transaction: Transaction): AppResult<Transaction>
    suspend fun delete(transactionId: String): AppResult<Unit>
}

interface BalanceRepository {
    /** Tính từ lịch sử bằng một câu SUM trên máy, tự cập nhật khi có giao dịch mới (D4). */
    fun observeBalances(): Flow<BalanceSummary>
}
