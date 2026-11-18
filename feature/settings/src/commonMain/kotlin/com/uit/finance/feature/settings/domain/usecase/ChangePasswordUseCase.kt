package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.settings.domain.model.PasswordChange
import com.uit.finance.feature.settings.domain.repository.AccountRepository

class ChangePasswordUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(): AppResult<PasswordChange> = repository.changePassword()
}
