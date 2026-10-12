@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.outbox

import com.uit.finance.core.common.id.UuidGenerator
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.database.FinanceDatabase
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.JsonObject

/** Bản ghi mà một op trỏ tới (ví của giao dịch, danh mục cha...). */
data class SyncRef(val entity: String, val entityId: String)

/**
 * Ghi op vào outbox. **Phải gọi bên trong `database.transaction { }` của lần ghi entity**, để entity và
 * op cùng thành công hoặc cùng huỷ: không bao giờ có thay đổi local mà không được gửi đi.
 *
 * Thứ tự gửi = thứ tự ghi, vì server áp từng op độc lập theo đúng thứ tự trong batch:
 * - Chỉ gộp vào op **cuối cùng của cả hàng** (sửa liên tiếp cùng một bản ghi). Gộp vào op nằm giữa hàng sẽ
 *   kéo trạng thái mới lên trước các op ghi sau nó, nên server kiểm luật theo thứ tự khác thứ user đã làm:
 *   op ghi sau có thể bị từ chối vì trạng thái "tương lai" của bản ghi nó trỏ tới.
 * - [SyncRef] ghi lại bản ghi mà op trỏ tới; op đổi bản ghi đó sau này sẽ đợi op này xong (Outbox.sq).
 *
 * Gộp thì đổi op id: kết quả của lần gửi dở với op id cũ không còn khớp dòng nào, bản mới được gửi lại.
 */
class OutboxWriter(
    private val uuidGenerator: UuidGenerator,
    private val clock: Clock,
) {

    fun upsert(
        database: FinanceDatabase,
        entity: String,
        entityId: String,
        payload: JsonObject,
        references: Collection<SyncRef> = emptyList(),
    ) {
        val queries = database.outboxQueries
        val now = clock.now().toEpochMilliseconds()
        val tail = queries.selectTail().executeAsOneOrNull()
        val seq = if (tail != null && tail.entity == entity && tail.entity_id == entityId && tail.action == OutboxAction.UPSERT.name) {
            queries.replace(opId = uuidGenerator.generate(), payload = payload.toString(), createdAt = now, seq = tail.seq)
            queries.deleteRefs(tail.seq)
            tail.seq
        } else {
            queries.insert(uuidGenerator.generate(), entity, entityId, OutboxAction.UPSERT.name, payload.toString(), now)
            queries.lastInsertedSeq().executeAsOne()
        }
        references
            .filterNot { it.entity == entity && it.entityId == entityId }
            .forEach { queries.insertRef(seq, it.entity, it.entityId) }
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
