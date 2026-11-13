package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.feature.settings.domain.model.AccountProfile
import com.uit.finance.feature.settings.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow

class ObserveAccountProfileUseCase(private val repository: AccountRepository) {
    operator fun invoke(): Flow<AccountProfile> = repository.profile()
}
