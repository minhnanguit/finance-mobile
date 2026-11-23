package com.uit.finance.feature.home.domain.usecase

import com.uit.finance.core.capture.domain.repository.CaptureRepository
import kotlinx.coroutines.flow.Flow

/** Số mục đang chờ user (giao dịch nháp từ khoản định kỳ hay thông báo ngân hàng, tin cần chọn ví): số trên chuông. */
class ObservePendingReviewCountUseCase(private val capture: CaptureRepository) {
    operator fun invoke(): Flow<Int> = capture.observePendingCount()
}
