package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveSessionUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Flow<Session?> = repository.observeSession().distinctUntilChanged()
}
