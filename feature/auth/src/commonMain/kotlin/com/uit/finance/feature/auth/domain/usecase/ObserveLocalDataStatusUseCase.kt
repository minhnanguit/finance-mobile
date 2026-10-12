package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.feature.auth.domain.model.LocalDataStatus
import com.uit.finance.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveLocalDataStatusUseCase(private val repository: AuthRepository) {
    operator fun invoke(): Flow<LocalDataStatus> = repository.observeLocalDataStatus().distinctUntilChanged()
}
