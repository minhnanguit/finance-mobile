package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.feature.settings.domain.model.AccountDeletion
import com.uit.finance.feature.settings.domain.model.DeletionConfirmation
import com.uit.finance.feature.settings.domain.repository.AccountRepository

/** Chữ xác nhận sai thì dừng ngay, không mở trình duyệt (ADR-008 A3). */
class DeleteAccountUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(confirmation: String): AppResult<AccountDeletion> {
        if (!DeletionConfirmation.matches(confirmation)) {
            return AppResult.Failure(
                AppError.Validation(listOf(FieldError(CONFIRMATION_FIELD, "Gõ ${DeletionConfirmation.WORD} để xác nhận."))),
            )
        }
        return repository.deleteAccount()
    }

    companion object {
        const val CONFIRMATION_FIELD: String = "confirmation"
    }
}
