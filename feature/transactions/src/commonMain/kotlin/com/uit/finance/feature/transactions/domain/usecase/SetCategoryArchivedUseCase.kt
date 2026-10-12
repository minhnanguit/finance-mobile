package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.repository.CategoryRepository

/** Lưu trữ danh mục: không chọn được cho giao dịch mới, giao dịch cũ vẫn giữ danh mục đó (D7). */
class SetCategoryArchivedUseCase(private val categories: CategoryRepository) {
    suspend operator fun invoke(categoryId: String, archived: Boolean): AppResult<Unit> = categories.setArchived(categoryId, archived)
}
