package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.feature.auth.domain.repository.AuthRepository

/** Còn bao nhiêu thay đổi chưa lên server: đăng xuất lúc này sẽ mất chúng. */
class CountUnsentChangesUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Long = repository.unsentChangeCount()
}
