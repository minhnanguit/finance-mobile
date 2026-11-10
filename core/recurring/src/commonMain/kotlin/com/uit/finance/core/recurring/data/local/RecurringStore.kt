package com.uit.finance.core.recurring.data.local

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
 * Cửa duy nhất để recurring đọc/ghi DB của user đang đăng nhập (cùng khuôn `BudgetStore`): đọc là `Flow` tự chuyển
 * DB khi đổi user; ghi là **một transaction** cho bản ghi + outbox, xong thì hẹn sync.
 */
internal class RecurringStore(
    private val databases: UserDatabaseProvider,
    private val scheduler: SyncScheduler,
    private val dispatchers: DispatcherProvider,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> observe(empty: T, query: (FinanceDatabase) -> Flow<T>): Flow<T> =
        databases.database.flatMapLatest { database -> database?.let(query) ?: flowOf(empty) }

    suspend fun <T> read(empty: T, block: (FinanceDatabase) -> T): T = withContext(dispatchers.io) {
        databases.database.value?.let(block) ?: empty
    }

    suspend fun <T> write(block: (FinanceDatabase) -> T): AppResult<T> = withContext(dispatchers.io) {
        val database = databases.database.value
            ?: return@withContext AppResult.Failure(AppError.Storage(NO_ACTIVE_USER))
        try {
            val result = database.transactionWithResult { block(database) }
            scheduler.requestImmediate()
            AppResult.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecurringRuleException) {
            AppResult.Failure(AppError.Validation(e.errors))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Storage(e::class.simpleName))
        }
    }

    private companion object {
        const val NO_ACTIVE_USER = "no active user database"
    }
}

internal class RecurringRuleException(val errors: List<FieldError>) : RuntimeException()

internal fun rejectWith(field: String, code: String): Nothing = throw RecurringRuleException(listOf(FieldError(field, code)))
