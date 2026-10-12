package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.feature.auth.domain.repository.AuthRepository

/** Thử mở lại sổ (gọi lại `/me` nếu lần trước mất mạng). Kết quả đến qua [ObserveLocalDataStatusUseCase]. */
class RetryLocalDataUseCase(private val repository: AuthRepository) {
    operator fun invoke() = repository.retryLocalData()
}
