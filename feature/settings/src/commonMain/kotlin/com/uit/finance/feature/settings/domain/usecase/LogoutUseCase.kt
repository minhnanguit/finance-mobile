package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.settings.domain.repository.AccountRepository

class LogoutUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.logout()
}
