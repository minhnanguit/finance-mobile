@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.session

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabases
import com.uit.finance.core.datastore.session.SessionStore
import com.uit.finance.core.datastore.session.StoredSession
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.model.UserProfileDto
import com.uit.finance.core.sync.scheduler.SyncScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class DefaultUserSessionTest {

    private class FakeSessions : SessionStore {
        override val session = MutableStateFlow<StoredSession?>(null)
        override suspend fun current() = session.value
        override suspend fun save(session: StoredSession) { this.session.value = session }
        override suspend fun attachUserId(userId: String) { session.value = session.value?.copy(userId = userId) }
        override suspend fun clear() { session.value = null }
    }

    /** Ghi lại thao tác thay vì mở DB thật: DB thật cần SQLCipher của nền tảng. */
    private class FakeDatabases : UserDatabases {
        val events = mutableListOf<String>()
        val stored = mutableSetOf<String>()
        override val database: StateFlow<FinanceDatabase?> = MutableStateFlow(null)
        override var activeUserId: String? = null
        var failOpen = false

        override suspend fun open(userId: String) {
            if (failOpen) throw IllegalStateException("cannot open")
            events += "open:$userId"
            activeUserId = userId
            stored += userId
        }

        override suspend fun close() {
            events += "close"
            activeUserId = null
        }

        override suspend fun delete(userId: String) {
            events += "delete:$userId"
            stored -= userId
            if (activeUserId == userId) activeUserId = null
        }

        override suspend fun storedUserIds() = stored.toSet()
    }

    private class FakeScheduler : SyncScheduler {
        val calls = mutableListOf<String>()
        override fun schedulePeriodic() { calls += "periodic" }
        override fun requestImmediate() { calls += "now" }
        override fun cancelAll() { calls += "cancel" }
    }

    private class FakeUserApi(var id: String = "user-a") : UserApi {
        var offline = false
        var calls = 0
        override suspend fun getCurrentUser(): UserProfileDto {
            calls++
            if (offline) throw IOException("offline")
            return UserProfileDto(id, "a@example.com", "A", Instant.fromEpochSeconds(0))
        }
    }

    private val sessions = FakeSessions()
    private val databases = FakeDatabases()
    private val scheduler = FakeScheduler()
    private val userApi = FakeUserApi()

    private fun TestScope.session() =
        DefaultUserSession(sessions, userApi, databases, scheduler, backgroundScope, Logger.withTag("test")).also { it.start() }

    private fun signedIn(userId: String? = null) =
        StoredSession(accessToken = "at", refreshToken = "rt", accessTokenExpiresAtEpochSeconds = 0, userId = userId)

    @Test
    fun `có token mà chưa biết userId thì gọi me, gắn userId, rồi mở đúng sổ và hẹn sync`() = runTest {
        val session = session()

        sessions.save(signedIn())
        runCurrent()

        assertEquals("user-a", sessions.current()?.userId)
        assertEquals(listOf("open:user-a"), databases.events.filter { it.startsWith("open") })
        assertEquals(LocalDataState.Ready("user-a"), session.state.value)
        assertTrue(scheduler.calls.containsAll(listOf("periodic", "now")))
    }

    @Test
    fun `token rotate không mở lại DB`() = runTest {
        session()
        sessions.save(signedIn("user-a"))
        runCurrent()

        sessions.save(signedIn("user-a").copy(accessToken = "rotated"))
        runCurrent()

        assertEquals(1, databases.events.count { it.startsWith("open") })
    }

    @Test
    fun `hết phiên chỉ đóng DB, không xoá (B7)`() = runTest {
        val session = session()
        sessions.save(signedIn("user-a"))
        runCurrent()

        sessions.clear() // refresh token bị Keycloak từ chối
        runCurrent()

        assertTrue("close" in databases.events)
        assertTrue(databases.events.none { it.startsWith("delete") })
        assertEquals(setOf("user-a"), databases.storedUserIds())
        assertEquals(LocalDataState.SignedOut, session.state.value)
    }

    @Test
    fun `đăng xuất thì dừng sync rồi xoá sổ của user hiện tại (B5)`() = runTest {
        val session = session()
        sessions.save(signedIn("user-a"))
        runCurrent()

        session.wipeCurrentUser()

        assertTrue("delete:user-a" in databases.events)
        assertEquals("cancel", scheduler.calls.last())
    }

    @Test
    fun `lần đầu mà mất mạng thì Failed, có mạng lại thì refresh mở được sổ`() = runTest {
        val session = session()
        userApi.offline = true

        sessions.save(signedIn())
        runCurrent()
        val failed = session.state.value
        userApi.offline = false
        session.refresh()
        runCurrent()

        assertTrue(failed is LocalDataState.Failed && failed.error is AppError.Network)
        assertEquals(LocalDataState.Ready("user-a"), session.state.value)
    }

    @Test
    fun `không mở được DB thì Failed Storage, không hẹn sync`() = runTest {
        val session = session()
        databases.failOpen = true

        sessions.save(signedIn("user-a"))
        runCurrent()

        assertTrue((session.state.value as LocalDataState.Failed).error is AppError.Storage)
        assertTrue("now" !in scheduler.calls)
    }

    @Test
    fun `biết được sổ của user khác còn trên máy và xoá được`() = runTest {
        val session = session()
        databases.stored += "user-old"
        sessions.save(signedIn("user-a"))
        runCurrent()

        val others = session.otherUsersOnDevice("user-a")
        session.wipe(others)

        assertEquals(setOf("user-old"), others)
        assertEquals(setOf("user-a"), databases.storedUserIds())
    }
}
