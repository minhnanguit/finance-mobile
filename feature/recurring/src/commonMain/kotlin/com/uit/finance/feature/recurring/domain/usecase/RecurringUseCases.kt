package com.uit.finance.feature.recurring.domain.usecase

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.common.time.observeToday
import com.uit.finance.core.ledger.domain.model.TransactionStatus
import com.uit.finance.core.ledger.domain.repository.AccountRepository
import com.uit.finance.core.ledger.domain.repository.CategoryRepository
import com.uit.finance.core.ledger.domain.repository.TransactionRepository
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringDraft
import com.uit.finance.core.recurring.domain.model.RevisionChange
import com.uit.finance.core.recurring.domain.repository.RecurringRepository
import com.uit.finance.core.recurring.domain.validation.RecurringRules
import com.uit.finance.core.recurring.reminder.NotificationPermissionGate
import com.uit.finance.feature.recurring.domain.model.RecurringFormData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDate

/** Ví/danh mục chưa lưu trữ + khoản đang sửa (nếu có), tự cập nhật theo DB. */
class ObserveRecurringFormDataUseCase(
    private val recurringRepository: RecurringRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) {
    operator fun invoke(recurringId: String?): Flow<RecurringFormData> = combine(
        accountRepository.observeAccounts(),
        categoryRepository.observeCategories(),
        recurringRepository.observeRecurrings(),
        observeToday(clock),
    ) { accounts, categories, recurrings, today ->
        RecurringFormData(
            accounts = accounts.filterNot { it.archived },
            categories = categories.filterNot { it.archived },
            recurring = recurringId?.let { id -> recurrings.firstOrNull { it.id == id } },
            today = today,
        )
    }
}

class CreateRecurringUseCase(private val repository: RecurringRepository) {
    suspend operator fun invoke(draft: RecurringDraft): AppResult<Recurring> = repository.create(draft)
}

/**
 * Lưu form sửa: tên / nhắc áp ngay; thay đổi lịch, số tiền, ví, danh mục, ghi chú, tự xác nhận thành phiên bản mới từ
 * `change.effectiveFrom` (R10). Mỗi phần là một lần ghi có điều kiện riêng.
 */
class UpdateRecurringUseCase(private val repository: RecurringRepository) {
    suspend operator fun invoke(
        recurringId: String,
        name: String?,
        remindDaysBefore: Int?,
        detailsChanged: Boolean,
        change: RevisionChange?,
    ): AppResult<Unit> {
        val updateDetails = detailsChanged && name != null
        // Kiểm tên/nhắc trước khi ghi gì; phiên bản mới (nhiều luật hơn, cần "hôm nay") ghi trước. Một phần hỏng thì
        // không phần nào được lưu, trừ lỗi lưu trữ.
        if (updateDetails) {
            val errors = RecurringRules.name(name) + RecurringRules.remind(remindDaysBefore)
            if (errors.isNotEmpty()) return AppResult.Failure(AppError.Validation(errors))
        }
        if (change != null) {
            val result = repository.revise(recurringId, change)
            if (result is AppResult.Failure) return result
        }
        if (updateDetails) {
            val result = repository.updateDetails(recurringId, name, remindDaysBefore)
            if (result is AppResult.Failure) return result
        }
        return AppResult.Success(Unit)
    }
}

/** Kết thúc: [lastDate] là ngày cuối còn sinh kỳ (R11). */
class EndRecurringUseCase(private val repository: RecurringRepository) {
    suspend operator fun invoke(recurringId: String, lastDate: LocalDate): AppResult<Recurring> = repository.end(recurringId, lastDate)
}

class DeleteRecurringUseCase(private val repository: RecurringRepository) {
    suspend operator fun invoke(recurringId: String): AppResult<Unit> = repository.delete(recurringId)
}

/** Gỡ chặn R13: ghi bù các kỳ bị chặn thành nháp, hoặc bỏ qua chúng. */
class ResolveBlockedRecurringUseCase(private val repository: RecurringRepository) {
    suspend operator fun invoke(recurringId: String, backfill: Boolean): AppResult<Recurring> =
        if (backfill) repository.backfillBlocked(recurringId) else repository.skipBlocked(recurringId)
}

/** Xác nhận một kỳ nháp: vào số dư, ngân sách, báo cáo (R5). Dùng luật ghi của ledger. */
class ConfirmOccurrenceUseCase(private val transactions: TransactionRepository) {
    suspend operator fun invoke(transactionId: String): AppResult<Unit> {
        val draft = transactions.observe(transactionId).firstOrNull()
            ?: return AppResult.Failure(AppError.Validation(listOf(FieldError("transaction", "ledger.not_found"))))
        if (draft.status == TransactionStatus.CONFIRMED) return AppResult.Success(Unit)
        return when (val result = transactions.update(draft.copy(status = TransactionStatus.CONFIRMED))) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }
}

/** Bỏ qua một kỳ nháp: xoá giao dịch đó, kỳ không bao giờ được sinh lại trên mọi máy (RS5). */
class SkipOccurrenceUseCase(private val transactions: TransactionRepository) {
    suspend operator fun invoke(transactionId: String): AppResult<Unit> = transactions.delete(transactionId)
}

/** Xin quyền thông báo khi user bật nhắc; từ chối không chặn việc lưu khoản. */
class RequestReminderPermissionUseCase(private val gate: NotificationPermissionGate) {
    suspend operator fun invoke(): Boolean = gate.isGranted() || gate.request()
}
