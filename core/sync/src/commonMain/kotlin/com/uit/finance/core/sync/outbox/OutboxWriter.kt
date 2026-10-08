@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.outbox

import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.FinanceDatabase
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.JsonObject

/**
 * Ghi op vào outbox. **Phải gọi bên trong `database.transaction { }` của lần ghi entity**, để entity và
 * op cùng thành công hoặc cùng huỷ: không bao giờ có thay đổi local mà không được gửi đi.
 *
 * Sửa lại một bản ghi còn đang chờ gửi thì cập nhật op cũ **tại chỗ** (op id mới, payload mới, giữ
 * vị trí trong hàng). Vừa không gửi thừa, vừa giữ thứ tự: op tạo ví vẫn đi trước op của giao dịch trỏ
 * tới ví đó. Op cũ có đang được gửi dở cũng không sao: kết quả của op id cũ không còn khớp dòng nào.
 */
class OutboxWriter(
    private val uuidGenerator: UuidGenerator,
    private val clock: Clock,
) {

    fun upsert(database: FinanceDatabase, entity: String, entityId: String, payload: JsonObject) {
        val queries = database.outboxQueries
        val latest = queries.latestFor(entity, entityId).executeAsOneOrNull()
        val now = clock.now().toEpochMilliseconds()
        if (latest?.action == OutboxAction.UPSERT.name) {
            queries.replace(opId = uuidGenerator.generate(), payload = payload.toString(), createdAt = now, seq = latest.seq)
        } else {
            queries.insert(uuidGenerator.generate(), entity, entityId, OutboxAction.UPSERT.name, payload.toString(), now)
        }
    }

    fun delete(database: FinanceDatabase, entity: String, entityId: String) {
        database.outboxQueries.insert(
            uuidGenerator.generate(),
            entity,
            entityId,
            OutboxAction.DELETE.name,
            null,
            clock.now().toEpochMilliseconds(),
        )
    }
}
