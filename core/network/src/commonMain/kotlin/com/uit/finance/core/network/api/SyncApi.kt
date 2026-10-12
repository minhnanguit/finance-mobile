package com.uit.finance.core.network.api

import com.uit.finance.core.network.api.model.SyncOpResultDto
import com.uit.finance.core.network.api.model.SyncOperationDto
import com.uit.finance.core.network.api.model.SyncPageDto

/**
 * Tag `sync` của contract 2.1.0 (ADR-002). Implementation throw [ApiException] khi non-2xx; bọc lời gọi
 * bằng [apiCall] để nhận `AppResult`.
 */
interface SyncApi {

    /**
     * Gửi 1–100 op. Kết quả trả theo đúng thứ tự op.
     *
     * @param idempotencyKey giữ nguyên khi gửi lại **cùng batch** (retry vì mất mạng): server trả lại
     *     response cũ thay vì xử lý lần hai
     */
    suspend fun push(deviceId: String, ops: List<SyncOperationDto>, idempotencyKey: String): List<SyncOpResultDto>

    /** Một trang thay đổi có `changeSeq` lớn hơn [since]; `null` = từ đầu. */
    suspend fun pull(since: String?, limit: Int): SyncPageDto

    companion object {
        const val MAX_OPS_PER_PUSH: Int = 100
        const val MAX_PULL_LIMIT: Int = 500
    }
}
