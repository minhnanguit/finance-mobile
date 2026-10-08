@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.outbox

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class OutboxAction { UPSERT, DELETE }

/**
 * Một op đang chờ gửi (ADR-002 §4).
 *
 * @param payload JSON của `data` theo contract; `null` khi [OutboxAction.DELETE]
 * @param attempts số lần server trả `RETRY`. Lỗi mạng không tính, để offline lâu không làm op bị bỏ
 */
data class OutboxEntry(
    val opId: String,
    val entity: String,
    val entityId: String,
    val action: OutboxAction,
    val payload: String?,
    val createdAt: Instant,
    val attempts: Int = 0,
    val lastError: String? = null,
) {
    /** Không in payload: có thể chứa số tiền, ghi chú (ADR-006 B8). */
    override fun toString(): String =
        "OutboxEntry(opId=$opId, entity=$entity, entityId=$entityId, action=$action, attempts=$attempts)"
}
