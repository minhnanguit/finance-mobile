package com.uit.finance.feature.budgets.domain.usecase

import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.domain.model.BudgetDraft
import com.uit.finance.core.budget.domain.repository.BudgetRepository
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.feature.budgets.domain.model.BudgetInput
import kotlinx.coroutines.flow.first

/** Tạo mới, hoặc đổi số tiền của ngân sách đã có (chỉ số tiền đổi được, G8). */
class SaveBudgetUseCase(
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(input: BudgetInput): AppResult<Budget> {
        if (input.id != null) return budgets.updateAmount(input.id, input.amountMinor)
        val countable = countableCategoryIds()
        return budgets.create(BudgetDraft(input.categoryId, input.currency, input.amountMinor, input.startMonth)) {
            it.categoryId in countable
        }
    }

    /**
     * Danh mục mà ngân sách của nó còn tính vào giới hạn 100: chi, cấp 1, chưa xoá (kể cả đã lưu trữ, G13). Giống
     * server (ADR-007 GS8): danh mục đã bị xoá hoặc đổi thành con ở máy khác thì ngân sách của nó không chiếm chỗ,
     * dù tombstone ngân sách chưa sync về. Danh mục chưa có trên máy cũng không được đếm; nếu thật sự đã đủ 100
     * thì server trả `budget.limit_reached` và bản local bị bỏ.
     */
    private suspend fun countableCategoryIds(): Set<String> =
        categories.observeCategories().first()
            .filter { it.kind == CategoryKind.EXPENSE && it.parentId == null }
            .map { it.id }
            .toSet()
}
