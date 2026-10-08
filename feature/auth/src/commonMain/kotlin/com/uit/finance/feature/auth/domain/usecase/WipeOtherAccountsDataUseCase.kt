package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.repository.AuthRepository

class WipeOtherAccountsDataUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.wipeOtherAccounts()
}
