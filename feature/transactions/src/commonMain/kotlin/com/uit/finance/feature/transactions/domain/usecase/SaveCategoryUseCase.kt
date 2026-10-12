package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.asFailure
import com.uit.finance.core.common.result.map
import com.uit.finance.core.ledger.domain.model.CategoryDraft
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.feature.transactions.domain.model.CategoryInput
import kotlinx.coroutines.flow.first

/** Tạo hoặc sửa danh mục. Sửa giữ loại thu/chi, danh mục cha, mã mẫu (`template_key`) và trạng thái lưu trữ. */
class SaveCategoryUseCase(private val categories: CategoryRepository) {
    suspend operator fun invoke(input: CategoryInput): AppResult<Unit> {
        if (input.id == null) {
            return categories.create(CategoryDraft(kind = input.kind, name = input.name, icon = input.icon, color = input.color)).map { }
        }
        val existing = categories.observeCategories().first().firstOrNull { it.id == input.id }
            ?: return AppError.Validation("category", LedgerErrorCode.NOT_FOUND).asFailure()
        return categories.update(existing.copy(name = input.name, icon = input.icon, color = input.color)).map { }
    }
}
