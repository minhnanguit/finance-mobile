package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.feature.auth.domain.repository.AuthRepository

/** Màn đăng nhập có nên cho "Xoá dữ liệu trên máy này" không: chưa ai đăng nhập mà máy vẫn còn sổ. */
class CheckLocalDataOnDeviceUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Boolean = repository.hasLocalDataWithoutSession()
}
