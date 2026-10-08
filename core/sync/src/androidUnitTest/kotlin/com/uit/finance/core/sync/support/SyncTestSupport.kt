package com.uit.finance.core.sync.support

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabaseProvider
import com.uit.finance.core.sync.engine.PullPage
import com.uit.finance.core.sync.engine.PushResult
import com.uit.finance.core.sync.engine.RemoteChange
import com.uit.finance.core.sync.engine.SyncChangeApplier
import com.uit.finance.core.sync.engine.SyncOutcome
import com.uit.finance.core.sync.engine.SyncRemoteDataSource
import com.uit.finance.core.sync.outbox.OutboxEntry
import kotlinx.coroutines.flow.MutableStateFlow

/** Schema thật trên SQLite in-memory của JVM (không có SQLCipher, không cần ở đây). */
fun inMemoryDatabase(): FinanceDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    FinanceDatabase.Schema.create(driver)
    return FinanceDatabase(driver)
}

class FakeDatabases(database: FinanceDatabase? = inMemoryDatabase()) : UserDatabaseProvider {
    override val database = MutableStateFlow(database)
}

/** Server giả: mặc định APPLIED cho mọi op và pull rỗng. */
class FakeRemote : SyncRemoteDataSource {
    val pushedBatches = mutableListOf<List<OutboxEntry>>()
    val idempotencyKeys = mutableListOf<String>()
    val pulledCursors = mutableListOf<String?>()
    val pages = ArrayDeque<AppResult<PullPage>>()

    var onPush: (List<OutboxEntry>) -> AppResult<List<PushResult>> = { batch ->
        AppResult.Success(batch.map { PushResult(it.opId, SyncOutcome.APPLIED, null, null) })
    }

    override suspend fun push(deviceId: String, batch: List<OutboxEntry>, idempotencyKey: String): AppResult<List<PushResult>> {
        pushedBatches += batch
        idempotencyKeys += idempotencyKey
        return onPush(batch)
    }

    override suspend fun pull(since: String?, limit: Int): AppResult<PullPage> {
        pulledCursors += since
        return pages.removeFirstOrNull() ?: AppResult.Success(PullPage(emptyList(), since ?: "0", hasMore = false))
    }
}

class RecordingApplier(override val entity: String = "account") : SyncChangeApplier {
    val applied = mutableListOf<RemoteChange>()
    val discarded = mutableListOf<String>()

    override fun apply(database: FinanceDatabase, change: RemoteChange) {
        applied += change
    }

    override fun discard(database: FinanceDatabase, entityId: String) {
        discarded += entityId
    }
}

fun remote(id: String, seq: Long, deleted: Boolean = false, entity: String = "account") =
    RemoteChange(entity = entity, id = id, changeSeq = seq, deleted = deleted, data = null)
