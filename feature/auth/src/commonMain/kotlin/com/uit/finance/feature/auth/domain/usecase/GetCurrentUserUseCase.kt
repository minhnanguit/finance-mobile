package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.model.UserProfile
import com.uit.finance.feature.auth.domain.repository.AuthRepository

class GetCurrentUserUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AppResult<UserProfile> = repository.currentUser()
}
