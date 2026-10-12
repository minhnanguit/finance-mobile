package com.uit.finance.core.sync.domain.usecase

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.sync.engine.SyncEngine
import com.uit.finance.core.sync.engine.SyncReport
import com.uit.finance.core.sync.outbox.OutboxRepository

/**
 * User chủ động làm mới (kéo xuống): bỏ thời gian chờ backoff của mọi thay đổi rồi chạy một vòng sync và
 * chờ nó xong, để UI biết khi nào tắt vòng xoay và báo lỗi mạng nếu có. Engine tự gộp các lần gọi chồng nhau.
 */
class SyncNowUseCase(
    private val outbox: OutboxRepository,
    private val engine: SyncEngine,
) {
    suspend operator fun invoke(): AppResult<SyncReport> {
        outbox.retryAllNow()
        return engine.sync()
    }
}
