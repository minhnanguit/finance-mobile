package com.mosaicglobal.finance.feature.auth.domain.usecase

import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
import com.mosaicglobal.finance.feature.auth.domain.repository.AuthRepository

class GetCurrentUserUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AppResult<UserProfile> = repository.currentUser()
}
