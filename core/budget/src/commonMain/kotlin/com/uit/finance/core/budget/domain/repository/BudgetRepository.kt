package com.uit.finance.core.budget.domain.repository

import com.uit.finance.core.budget.domain.model.Budget
import com.uit.finance.core.budget.domain.model.BudgetDraft
import com.uit.finance.core.common.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Ngân sách của user đang đăng nhập (ADR-007). Mọi lần ghi lưu vào DB trên máy rồi mới gửi lên server khi có
 * mạng: thành công nghĩa là đã lưu ở máy. Vi phạm luật trả `AppError.Validation` với mã trong
 * `BudgetErrorCode`. Chưa mở sổ của user nào: `AppError.Storage`.
 *
 * Danh mục có hợp lệ không (chi, cấp 1, chưa xoá) là việc của người gọi, vì module này không đọc bảng danh
 * mục; server kiểm lại và trả `budget.invalid_category` nếu sai.
 */
interface BudgetRepository {
    /** Ngân sách chưa xoá, tự đổi theo user đang đăng nhập. */
    fun observeBudgets(): Flow<List<Budget>>

    /** Cặp (danh mục, tiền tệ) đã có: `budget.duplicate` (G3). Quá 100: `budget.limit_reached`. */
    suspend fun create(draft: BudgetDraft): AppResult<Budget>

    /** Chỉ số tiền sửa được (G8); áp cho mọi tháng (G5). */
    suspend fun updateAmount(budgetId: String, amountMinor: Long): AppResult<Budget>

    suspend fun delete(budgetId: String): AppResult<Unit>
}
