package com.uit.finance.core.budget.testing

import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.domain.model.BudgetDraft
import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fake trong bộ nhớ cho [BudgetRepository]. Không kiểm luật nghiệp vụ (đó là việc của `BudgetRepositoryTest`
 * chạy trên SQLite thật); chỉ lưu, phát Flow và ghi lại lời gọi để test ViewModel / use case. Đặt [failNext]
 * để lần ghi kế tiếp trả lỗi.
 */
class FakeBudgetRepository : BudgetRepository {
    val budgets = MutableStateFlow<List<Budget>>(emptyList())

    /** Lỗi trả cho lần ghi kế tiếp (rồi tự xoá). */
    var failNext: AppError? = null

    val created = mutableListOf<BudgetDraft>()
    val deleted = mutableListOf<String>()
    private var nextId = 0

    override fun observeBudgets(): Flow<List<Budget>> = budgets

    override suspend fun create(draft: BudgetDraft): AppResult<Budget> = write {
        created += draft
        Budget(
            id = "budget-${++nextId}",
            categoryId = draft.categoryId,
            amount = Money(draft.amountMinor, draft.currency),
            startMonth = draft.startMonth,
        ).also { budget -> budgets.update { it + budget } }
    }

    override suspend fun updateAmount(budgetId: String, amountMinor: Long): AppResult<Budget> = write {
        val updated = budgets.value.first { it.id == budgetId }.let { it.copy(amount = Money(amountMinor, it.currency)) }
        budgets.update { list -> list.map { if (it.id == budgetId) updated else it } }
        updated
    }

    override suspend fun delete(budgetId: String): AppResult<Unit> = write {
        deleted += budgetId
        budgets.update { list -> list.filterNot { it.id == budgetId } }
    }

    private inline fun <T> write(block: () -> T): AppResult<T> {
        failNext?.let {
            failNext = null
            return AppResult.Failure(it)
        }
        return AppResult.Success(block())
    }
}
