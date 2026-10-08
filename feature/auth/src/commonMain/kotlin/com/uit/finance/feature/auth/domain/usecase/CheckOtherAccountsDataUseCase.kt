package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.repository.AuthRepository

/** Sau khi đăng nhập: máy này còn sổ của tài khoản nào khác không (ADR-006 B5). */
class CheckOtherAccountsDataUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AppResult<Int> = repository.otherAccountsOnDevice()
}
