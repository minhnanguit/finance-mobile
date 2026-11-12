package com.uit.finance.feature.auth.domain.usecase

import com.uit.finance.feature.auth.domain.repository.AuthRepository

/** User đã đọc thông báo (hoặc đăng nhập lại): không hiện nữa. */
class AcknowledgeSignOutNoticeUseCase(private val repository: AuthRepository) {
    operator fun invoke() = repository.acknowledgeSignOutNotice()
}
