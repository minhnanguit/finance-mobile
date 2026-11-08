package com.uit.finance.core.budget.data.local

import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.sync.scheduler.SyncScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * Cửa duy nhất để budget đọc/ghi DB của user đang đăng nhập (cùng khuôn với `LedgerStore` của core/ledger).
 *
 * - Đọc: `Flow` tự chuyển sang DB mới khi đổi user; chưa có user thì phát [empty].
 * - Ghi: **một transaction** cho cả bản ghi lẫn op trong outbox (+ `outbox_ref`); vi phạm luật giữa chừng
 *   ([rejectWith]) thì huỷ cả hai. Ghi xong thì hẹn sync ngay.
 */
internal class BudgetStore(
    private val databases: UserDatabaseProvider,
    private val scheduler: SyncScheduler,
    private val dispatchers: DispatcherProvider,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(empty: T, query: (FinanceDatabase) -> Flow<T>): Flow<T> =
        databases.database.flatMapLatest { database -> database?.let(query) ?: flowOf(empty) }

    suspend fun <T> write(block: (FinanceDatabase) -> T): AppResult<T> = withContext(dispatchers.io) {
        val database = databases.database.value
            ?: return@withContext AppResult.Failure(AppError.Storage(NO_ACTIVE_USER))
        try {
            val result = database.transactionWithResult { block(database) }
            scheduler.requestImmediate()
            AppResult.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BudgetRuleException) {
            AppResult.Failure(AppError.Validation(e.errors))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Storage(e::class.simpleName))
        }
    }

    private companion object {
        const val NO_ACTIVE_USER = "no active user database"
    }
}

/** Vi phạm luật phát hiện bên trong transaction: ném ra để rollback, [BudgetStore.write] đổi thành `Validation`. */
internal class BudgetRuleException(val errors: List<FieldError>) : RuntimeException()

internal fun rejectWith(field: String, code: String): Nothing = throw BudgetRuleException(listOf(FieldError(field, code)))
