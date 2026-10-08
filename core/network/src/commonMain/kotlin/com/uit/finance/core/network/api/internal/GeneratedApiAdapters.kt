package com.uit.finance.core.network.api.internal

import com.uit.finance.core.network.api.SyncApi
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.model.AccountPayload
import com.uit.finance.core.network.api.model.CategoryPayload
import com.uit.finance.core.network.api.model.SyncActionDto
import com.uit.finance.core.network.api.model.SyncChangeDto
import com.uit.finance.core.network.api.model.SyncOpResultDto
import com.uit.finance.core.network.api.model.SyncOperationDto
import com.uit.finance.core.network.api.model.SyncOutcomeDto
import com.uit.finance.core.network.api.model.SyncPageDto
import com.uit.finance.core.network.api.model.SyncPayloadCodec
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.network.api.model.UserProfileDto
import com.uit.finance.core.network.generated.api.MeApi as GeneratedMeApi
import com.uit.finance.core.network.generated.api.SyncApi as GeneratedSyncApi
import com.uit.finance.core.network.generated.model.AccountData
import com.uit.finance.core.network.generated.model.CategoryData
import com.uit.finance.core.network.generated.model.SyncChange
import com.uit.finance.core.network.generated.model.SyncOpResult
import com.uit.finance.core.network.generated.model.SyncOperation
import com.uit.finance.core.network.generated.model.SyncPushRequest
import com.uit.finance.core.network.generated.model.TransactionData
import com.uit.finance.core.network.generated.model.UserProfile
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/*
 * File DUY NHẤT được import generated client (`...core.network.generated.*`). Nó map giữa wire model
 * generated và DTO nhỏ mà phần còn lại của app thấy, nên generate lại client (spec mới) chỉ ảnh hưởng
 * file này.
 *
 * Response non-2xx không bao giờ tới `body()`: response validator của HttpClient dùng chung đã throw
 * ApiException trước (xem HttpClientFactory).
 */

internal class GeneratedUserApiAdapter(
    baseUrl: String,
    httpClient: HttpClient,
) : UserApi {

    private val delegate = GeneratedMeApi(baseUrl = baseUrl, httpClient = httpClient)

    override suspend fun getCurrentUser(): UserProfileDto = delegate.getCurrentUser().body().toDto()
}

private fun UserProfile.toDto() = UserProfileDto(
    id = id,
    email = email,
    displayName = displayName,
    createdAt = createdAt,
)

// ---- Sync (contract 2.1.0) ----

internal class GeneratedSyncApiAdapter(
    baseUrl: String,
    httpClient: HttpClient,
) : SyncApi {

    private val delegate = GeneratedSyncApi(baseUrl = baseUrl, httpClient = httpClient)

    override suspend fun push(
        deviceId: String,
        ops: List<SyncOperationDto>,
        idempotencyKey: String,
    ): List<SyncOpResultDto> {
        val request = SyncPushRequest(deviceId = deviceId, ops = ops.map { it.toWire() })
        return delegate.pushChanges(idempotencyKey = idempotencyKey, syncPushRequest = request)
            .body().results.map { it.toDto() }
    }

    override suspend fun pull(since: String?, limit: Int): SyncPageDto {
        val page = delegate.pullChanges(since = since, limit = limit).body()
        return SyncPageDto(
            changes = page.changes.map { it.toDto() },
            nextCursor = page.nextCursor,
            hasMore = page.hasMore,
        )
    }
}

private fun SyncOperationDto.toWire() = SyncOperation(
    opId = opId,
    entity = entity,
    id = entityId,
    action = when (action) {
        SyncActionDto.UPSERT -> SyncOperation.Action.UPSERT
        SyncActionDto.DELETE -> SyncOperation.Action.DELETE
    },
    `data` = data,
)

private fun SyncOpResult.toDto() = SyncOpResultDto(
    opId = opId,
    outcome = SyncOutcomeDto.valueOf(outcome.value),
    code = code,
    current = current?.toDto(),
)

private fun SyncChange.toDto() = SyncChangeDto(
    entity = entity,
    id = id,
    changeSeq = changeSeq,
    deleted = deleted,
    data = `data`?.let(::JsonObject),
)

/** Mọi payload đi qua model generated, nên tên field và kiểu luôn khớp contract đang pin. */
internal class GeneratedSyncPayloadCodec(private val json: Json) : SyncPayloadCodec {

    override fun encode(payload: AccountPayload): JsonObject = json.encodeToJsonElement(
        AccountData.serializer(),
        AccountData(
            name = payload.name,
            type = enumOf(AccountData.Type.entries, payload.type) { it.value },
            currency = payload.currency,
            openingBalanceMinor = payload.openingBalanceMinor,
            sortOrder = payload.sortOrder,
            archived = payload.archived,
        ),
    ).jsonObject

    override fun encode(payload: CategoryPayload): JsonObject = json.encodeToJsonElement(
        CategoryData.serializer(),
        CategoryData(
            kind = enumOf(CategoryData.Kind.entries, payload.kind) { it.value },
            name = payload.name,
            archived = payload.archived,
            parentId = payload.parentId,
            icon = payload.icon,
            color = payload.color,
            // readOnly: không bao giờ gửi lên, server từ chối field này.
            templateKey = null,
        ),
    ).jsonObject

    override fun encode(payload: TransactionPayload): JsonObject = json.encodeToJsonElement(
        TransactionData.serializer(),
        TransactionData(
            type = enumOf(TransactionData.Type.entries, payload.type) { it.value },
            status = enumOf(TransactionData.Status.entries, payload.status) { it.value },
            accountId = payload.accountId,
            amountMinor = payload.amountMinor,
            currency = payload.currency,
            occurredOn = payload.occurredOn,
            counterAccountId = payload.counterAccountId,
            categoryId = payload.categoryId,
            occurredAt = payload.occurredAt,
            payee = payload.payee,
            note = payload.note,
        ),
    ).jsonObject

    override fun decodeAccount(data: JsonObject): AccountPayload =
        json.decodeFromJsonElement(AccountData.serializer(), data).let {
            AccountPayload(
                name = it.name,
                type = it.type.value,
                currency = it.currency,
                openingBalanceMinor = it.openingBalanceMinor,
                sortOrder = it.sortOrder,
                archived = it.archived,
            )
        }

    override fun decodeCategory(data: JsonObject): CategoryPayload =
        json.decodeFromJsonElement(CategoryData.serializer(), data).let {
            CategoryPayload(
                kind = it.kind.value,
                name = it.name,
                parentId = it.parentId,
                icon = it.icon,
                color = it.color,
                archived = it.archived,
                templateKey = it.templateKey,
            )
        }

    override fun decodeTransaction(data: JsonObject): TransactionPayload =
        json.decodeFromJsonElement(TransactionData.serializer(), data).let {
            TransactionPayload(
                type = it.type.value,
                status = it.status.value,
                accountId = it.accountId,
                counterAccountId = it.counterAccountId,
                amountMinor = it.amountMinor,
                currency = it.currency,
                categoryId = it.categoryId,
                occurredOn = it.occurredOn,
                occurredAt = it.occurredAt,
                payee = it.payee,
                note = it.note,
            )
        }

    private inline fun <E> enumOf(entries: List<E>, raw: String, value: (E) -> String): E =
        entries.firstOrNull { value(it) == raw } ?: throw IllegalArgumentException("Unknown enum value for sync payload")
}

