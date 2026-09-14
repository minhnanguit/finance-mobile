@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.core.sync.outbox

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.common.result.getOrNull
import com.mosaicglobal.finance.core.database.FinanceDatabase
import com.mosaicglobal.finance.core.testing.testDispatcherProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SqlDelightOutboxRepositoryTest {

    private fun TestScope.newRepository(): SqlDelightOutboxRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FinanceDatabase.Schema.create(driver)
        return SqlDelightOutboxRepository(FinanceDatabase(driver), testDispatcherProvider())
    }

    private fun entry(id: String, createdAt: Long) = OutboxEntry(
        id = id,
        entity = "transaction",
        op = OutboxOp.CREATE,
        payload = """{"id":"$id"}""",
        idempotencyKey = "key-$id",
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    @Test
    fun enqueueThenPendingReturnsInCreationOrder() = runTest {
        val repo = newRepository()

        repo.enqueue(entry("b", 2_000))
        repo.enqueue(entry("a", 1_000))

        val pending = repo.pending(limit = 10).getOrNull().orEmpty()
        assertEquals(listOf("a", "b"), pending.map { it.id })
        assertEquals(OutboxOp.CREATE, pending.first().op)
        assertEquals("key-a", pending.first().idempotencyKey)
    }

    @Test
    fun markFailedIncrementsAttemptsAndKeepsEntry() = runTest {
        val repo = newRepository()
        repo.enqueue(entry("a", 1_000))

        repo.markFailed("a", "HTTP 500")
        repo.markFailed("a", "HTTP 503")

        val stored = repo.pending(10).getOrNull().orEmpty().single()
        assertEquals(2, stored.attempts)
        assertEquals("HTTP 503", stored.lastError)
    }

    @Test
    fun removeDeletesAcknowledgedAndCountFlowUpdates() = runTest {
        val repo = newRepository()
        repo.enqueue(entry("a", 1_000))
        repo.enqueue(entry("b", 2_000))

        repo.observeCount().test {
            assertEquals(2L, awaitItem())
            repo.remove(listOf("a"))
            assertEquals(1L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("b"), repo.pending(10).getOrNull().orEmpty().map { it.id })
    }

    @Test
    fun duplicateIdIsAStorageFailureNotAnException() = runTest {
        val repo = newRepository()
        assertTrue(repo.enqueue(entry("a", 1_000)) is AppResult.Success)
        assertIs<AppResult.Failure>(repo.enqueue(entry("a", 1_000)))
    }
}
