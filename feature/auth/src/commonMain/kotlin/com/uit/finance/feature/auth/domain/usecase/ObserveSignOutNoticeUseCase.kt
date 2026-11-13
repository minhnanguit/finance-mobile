package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.feature.auth.domain.model.SignOutNotice
import com.uit.finance.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveSignOutNoticeUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Flow<SignOutNotice?> = repository.observeSignOutNotice().distinctUntilChanged()
}
