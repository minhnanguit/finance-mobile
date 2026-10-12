package com.uit.finance.feature.transactions.domain.usecase

import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow

/** Mọi danh mục chưa xoá, kể cả đã lưu trữ (màn quản lý danh mục tự tách). */
class ObserveCategoriesUseCase(private val categories: CategoryRepository) {
    operator fun invoke(): Flow<List<Category>> = categories.observeCategories()
}
