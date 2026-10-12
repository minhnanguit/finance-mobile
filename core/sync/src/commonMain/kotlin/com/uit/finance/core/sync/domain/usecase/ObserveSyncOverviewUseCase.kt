package com.uit.finance.core.sync.domain.usecase

import com.uit.finance.core.sync.domain.model.SyncOverview
import com.uit.finance.core.sync.engine.SyncEngine
import com.uit.finance.core.sync.engine.SyncStatus
import com.uit.finance.core.sync.issue.SyncIssueRepository
import com.uit.finance.core.sync.outbox.OutboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Gom hàng chờ, thay đổi bị kẹt, thay đổi bị từ chối và vòng sync đang chạy thành một [SyncOverview]. */
class ObserveSyncOverviewUseCase(
    private val outbox: OutboxRepository,
    private val issues: SyncIssueRepository,
    private val engine: SyncEngine,
) {
    operator fun invoke(): Flow<SyncOverview> = combine(
        outbox.observeUnsentCount(),
        outbox.observeStuckCount(),
        issues.observeUnacknowledgedCount(),
        engine.status,
    ) { pending, stuck, rejected, status ->
        SyncOverview(pending = pending, stuck = stuck, rejected = rejected, isSyncing = status == SyncStatus.Running)
    }.distinctUntilChanged()
}
