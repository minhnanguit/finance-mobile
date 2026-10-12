package com.uit.finance.core.sync.remote

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.network.api.SyncApi
import com.uit.finance.core.network.api.apiCall
import com.uit.finance.core.network.api.model.SyncActionDto
import com.uit.finance.core.network.api.model.SyncChangeDto
import com.uit.finance.core.network.api.model.SyncOperationDto
import com.uit.finance.core.network.api.model.SyncOutcomeDto
import com.uit.finance.core.sync.engine.PullPage
import com.uit.finance.core.sync.engine.PushResult
import com.uit.finance.core.sync.engine.RemoteChange
import com.uit.finance.core.sync.engine.SyncOutcome
import com.uit.finance.core.sync.engine.SyncRemoteDataSource
import com.uit.finance.core.sync.outbox.OutboxAction
import com.uit.finance.core.sync.outbox.OutboxEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** `POST /sync/push` + `GET /sync/pull` của contract 2.1.0, bọc bằng `apiCall` thành `AppResult`. */
internal class ApiSyncRemoteDataSource(
    private val api: SyncApi,
    private val json: Json,
) : SyncRemoteDataSource {

    override suspend fun push(deviceId: String, batch: List<OutboxEntry>, idempotencyKey: String): AppResult<List<PushResult>> =
        apiCall {
            api.push(deviceId = deviceId, ops = batch.map { it.toOperation() }, idempotencyKey = idempotencyKey)
                .map { PushResult(it.opId, it.outcome.toOutcome(), it.code, it.current?.toRemote()) }
        }

    override suspend fun pull(since: String?, limit: Int): AppResult<PullPage> = apiCall {
        val page = api.pull(since = since, limit = limit)
        PullPage(changes = page.changes.map { it.toRemote() }, nextCursor = page.nextCursor, hasMore = page.hasMore)
    }

    private fun OutboxEntry.toOperation() = SyncOperationDto(
        opId = opId,
        entity = entity,
        entityId = entityId,
        action = when (action) {
            OutboxAction.UPSERT -> SyncActionDto.UPSERT
            OutboxAction.DELETE -> SyncActionDto.DELETE
        },
        data = payload?.let { json.parseToJsonElement(it).jsonObject },
    )

    private fun SyncChangeDto.toRemote() = RemoteChange(
        entity = entity,
        id = id,
        changeSeq = changeSeq,
        deleted = deleted,
        data = data,
    )

    private fun SyncOutcomeDto.toOutcome() = when (this) {
        SyncOutcomeDto.APPLIED -> SyncOutcome.APPLIED
        SyncOutcomeDto.DUPLICATE -> SyncOutcome.DUPLICATE
        SyncOutcomeDto.CONFLICT -> SyncOutcome.CONFLICT
        SyncOutcomeDto.REJECTED -> SyncOutcome.REJECTED
        SyncOutcomeDto.RETRY -> SyncOutcome.RETRY
    }
}
