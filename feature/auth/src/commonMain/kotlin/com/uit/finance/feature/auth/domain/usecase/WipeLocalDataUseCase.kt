package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.auth.domain.repository.AuthRepository

/** Xoá mọi sổ còn trên máy khi chưa ai đăng nhập (user tự chọn, có hỏi lại). */
class WipeLocalDataUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.wipeLocalData()
}
