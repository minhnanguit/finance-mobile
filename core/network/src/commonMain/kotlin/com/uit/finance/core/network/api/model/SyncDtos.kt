@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.network.api.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject

/** Tên entity trên contract (`SyncOperation.entity`). */
object SyncEntity {
    const val ACCOUNT: String = "account"
    const val CATEGORY: String = "category"
    const val TRANSACTION: String = "transaction"
    const val BUDGET: String = "budget"
    const val RECURRING: String = "recurring"
}

/** [CREATE] (contract 2.3.0): chỉ ghi khi id chưa có; đã có (kể cả tombstone) → `CONFLICT sync.already_exists`. */
enum class SyncActionDto { UPSERT, DELETE, CREATE }

/** ADR-002 §4. Mọi kết quả trừ [RETRY] là chốt. */
enum class SyncOutcomeDto { APPLIED, DUPLICATE, CONFLICT, REJECTED, RETRY }

/**
 * Một op gửi lên.
 *
 * @param data `AccountData` / `CategoryData` / `TransactionData` đã mã hoá bằng [SyncPayloadCodec];
 *     `null` khi [SyncActionDto.DELETE]
 */
data class SyncOperationDto(
    val opId: String,
    val entity: String,
    val entityId: String,
    val action: SyncActionDto,
    val data: JsonObject?,
) {
    /** Không in [data]: có thể chứa số tiền, ghi chú (ADR-006 B8). */
    override fun toString(): String = "SyncOperationDto(opId=$opId, entity=$entity, action=$action)"
}

data class SyncOpResultDto(
    val opId: String,
    val outcome: SyncOutcomeDto,
    val code: String?,
    /** Bản ghi hiện tại của chính user trên server; `null` khi không có hoặc không phải của user. */
    val current: SyncChangeDto?,
)

/** Một bản ghi như server thấy, kể cả tombstone (`deleted = true`, `data = null`). */
data class SyncChangeDto(
    val entity: String,
    val id: String,
    val changeSeq: Long,
    val deleted: Boolean,
    val data: JsonObject?,
) {
    override fun toString(): String = "SyncChangeDto(entity=$entity, id=$id, changeSeq=$changeSeq, deleted=$deleted)"
}

data class SyncPageDto(
    val changes: List<SyncChangeDto>,
    val nextCursor: String,
    val hasMore: Boolean,
)

// ---- Payload theo entity (schema `AccountData` / `CategoryData` / `TransactionData`) ----

data class AccountPayload(
    val name: String,
    val type: String,
    val currency: String,
    val openingBalanceMinor: Long,
    val sortOrder: Int,
    val archived: Boolean,
) {
    override fun toString(): String = "AccountPayload(type=$type, currency=$currency, archived=$archived)"
}

data class CategoryPayload(
    val kind: String,
    val name: String,
    val parentId: String?,
    val icon: String?,
    val color: String?,
    val archived: Boolean,
    /** Chỉ có chiều server → client (readOnly). Gửi lên luôn bị bỏ qua. */
    val templateKey: String? = null,
) {
    override fun toString(): String = "CategoryPayload(kind=$kind, archived=$archived, templateKey=$templateKey)"
}

data class TransactionPayload(
    val type: String,
    val status: String,
    val accountId: String,
    val counterAccountId: String?,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String?,
    val occurredOn: LocalDate,
    val occurredAt: Instant?,
    val payee: String?,
    val note: String?,
) {
    /** Không in số tiền, người nhận, ghi chú (ADR-006 B8). */
    override fun toString(): String = "TransactionPayload(type=$type, status=$status, accountId=$accountId)"
}

/** Schema `BudgetData` (contract 2.2.0, ADR-007). `startMonth` luôn là ngày 1 của tháng. */
data class BudgetPayload(
    val categoryId: String,
    val currency: String,
    val amountMinor: Long,
    val startMonth: LocalDate,
) {
    /** Không in số tiền (ADR-006 B8). */
    override fun toString(): String = "BudgetPayload(categoryId=$categoryId, currency=$currency, startMonth=$startMonth)"
}

/**
 * Như [SyncPayloadCodec] cho entity `budget`. Tách riêng để module ledger không phải biết ngân sách tồn tại.
 */
interface BudgetPayloadCodec {
    fun encode(payload: BudgetPayload): JsonObject
    fun decodeBudget(data: JsonObject): BudgetPayload
}

/** Schema `RecurrenceRevisionData` (contract 2.3.0, ADR-008 R2). */
data class RecurrenceRevisionPayload(
    val id: String,
    val effectiveFrom: LocalDate,
    val anchorDate: LocalDate,
    val accountId: String,
    val categoryId: String,
    val amountMinor: Long,
    val note: String?,
    val frequency: String,
    val interval: Int,
    val weekday: Int?,
    val month: Int?,
    val dayOfMonth: Int?,
    val autoConfirm: Boolean,
) {
    /** Không in số tiền, ghi chú (ADR-006 B8). */
    override fun toString(): String = "RecurrenceRevisionPayload(id=$id, effectiveFrom=$effectiveFrom, frequency=$frequency)"
}

/** Schema `RecurringData` (contract 2.3.0, ADR-008). [revision] trong op = revision máy dựa vào; trong pull = hiện tại. */
data class RecurringPayload(
    val name: String,
    val type: String,
    val currency: String,
    val generationNotBefore: LocalDate,
    val generationEndDate: LocalDate?,
    val remindDaysBefore: Int?,
    val revision: Long,
    val revisions: List<RecurrenceRevisionPayload>,
) {
    /** Không in tên khoản (ADR-006 B8). */
    override fun toString(): String = "RecurringPayload(type=$type, currency=$currency, revision=$revision)"
}

/** Như [BudgetPayloadCodec] cho entity `recurring`. */
interface RecurringPayloadCodec {
    fun encode(payload: RecurringPayload): JsonObject
    fun decodeRecurring(data: JsonObject): RecurringPayload
}

/**
 * Payload ↔ JSON của `data`, đi qua model sinh từ contract: đổi tên field ở backend là build fail ở
 * đây, không lệch ngầm. Decode lỗi (field thiếu, enum lạ) ném `IllegalArgumentException` /
 * `SerializationException`; người gọi quyết định bỏ qua bản ghi đó.
 */
interface SyncPayloadCodec {
    fun encode(payload: AccountPayload): JsonObject
    fun encode(payload: CategoryPayload): JsonObject
    fun encode(payload: TransactionPayload): JsonObject

    fun decodeAccount(data: JsonObject): AccountPayload
    fun decodeCategory(data: JsonObject): CategoryPayload
    fun decodeTransaction(data: JsonObject): TransactionPayload
}
