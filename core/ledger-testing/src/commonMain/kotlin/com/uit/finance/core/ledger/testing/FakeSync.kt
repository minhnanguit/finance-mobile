package com.uit.finance.core.ledger.testing

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.sync.domain.usecase.AcknowledgeSyncIssuesUseCase
import com.uit.finance.core.sync.domain.usecase.ObserveSyncOverviewUseCase
import com.uit.finance.core.sync.domain.usecase.SyncNowUseCase
import com.uit.finance.core.sync.engine.SyncEngine
import com.uit.finance.core.sync.engine.SyncReport
import com.uit.finance.core.sync.engine.SyncStatus
import com.uit.finance.core.sync.issue.SyncIssueRepository
import com.uit.finance.core.sync.outbox.OutboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeOutboxRepository : OutboxRepository {
    val unsent = MutableStateFlow(0L)
    val stuck = MutableStateFlow(0L)
    var retryAllNowCalls = 0

    override fun observeUnsentCount(): Flow<Long> = unsent
    override suspend fun unsentCount(): Long = unsent.value
    override fun observeStuckCount(): Flow<Long> = stuck
    override suspend fun retryAllNow() {
        retryAllNowCalls++
    }
}

class FakeSyncIssueRepository : SyncIssueRepository {
    val unacknowledged = MutableStateFlow(0L)

    override fun observeUnacknowledgedCount(): Flow<Long> = unacknowledged
    override suspend fun acknowledgeAll() {
        unacknowledged.value = 0
    }
}

class FakeSyncEngine : SyncEngine {
    override val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    var result: AppResult<SyncReport> = AppResult.Success(SyncReport.EMPTY)
    var syncCalls = 0

    override suspend fun sync(): AppResult<SyncReport> {
        syncCalls++
        return result
    }

    override suspend fun stop() = Unit
}

/** Bộ fake sync đầy đủ và 3 use case thật dựng trên nó, để ViewModel test dùng đúng logic của core/sync. */
class FakeSyncKit {
    val outbox = FakeOutboxRepository()
    val issues = FakeSyncIssueRepository()
    val engine = FakeSyncEngine()

    val observeOverview = ObserveSyncOverviewUseCase(outbox, issues, engine)
    val syncNow = SyncNowUseCase(outbox, engine)
    val acknowledgeIssues = AcknowledgeSyncIssuesUseCase(issues)

    val status: StateFlow<SyncStatus> get() = engine.status
}
