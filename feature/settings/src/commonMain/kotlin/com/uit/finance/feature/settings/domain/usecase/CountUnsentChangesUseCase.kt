package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.feature.settings.domain.repository.AccountRepository

/** Còn bao nhiêu thay đổi chưa lên server: đăng xuất hay xoá tài khoản lúc này sẽ mất chúng. */
class CountUnsentChangesUseCase(private val repository: AccountRepository) {
    suspend operator fun invoke(): Long = repository.unsentChangeCount()
}
