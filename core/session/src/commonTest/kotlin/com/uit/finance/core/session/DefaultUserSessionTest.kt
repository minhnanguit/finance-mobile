@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.session

import co.touchlab.kermit.Logger
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.database.FinanceDatabase
import com.uit.finance.core.database.UserDatabases
import com.uit.finance.core.datastore.session.SessionStore
import com.uit.finance.core.datastore.session.StoredSession
import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.model.UserProfileDto
import com.uit.finance.core.sync.engine.SyncEngine
import com.uit.finance.core.sync.engine.SyncReport
import com.uit.finance.core.sync.engine.SyncStatus
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
        override suspend fun attachUserId(userId: String, forRefreshToken: String): Boolean {
            if (session.value?.refreshToken != forRefreshToken) return false
            session.value = session.value?.copy(userId = userId)
            return true
        }
        override suspend fun clear() { session.value = null }
    }

    /** Ghi lại thao tác thay vì mở DB thật: DB thật cần SQLCipher của nền tảng. */
    private class FakeDatabases(val events: MutableList<String> = mutableListOf()) : UserDatabases {
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

    private class FakeSyncEngine(private val events: MutableList<String>) : SyncEngine {
        override val status: StateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle)
        override suspend fun sync(): AppResult<SyncReport> = AppResult.Success(SyncReport.EMPTY)
        override suspend fun stop() { events += "stopSync" }
    }

    private class FakeUserApi(var id: String = "user-a") : UserApi {
        var offline = false
        var calls = 0
        /** Chạy giữa lúc `/me` đang bay, để mô phỏng phiên đổi giữa chừng. */
        var whileInFlight: () -> Unit = {}
        override suspend fun getCurrentUser(): UserProfileDto {
            calls++
            val answering = id // /me trả lời theo token lúc gửi, dù phiên có đổi trong lúc chờ
            whileInFlight()
            if (offline) throw IOException("offline")
            return UserProfileDto(answering, "a@example.com", "A", Instant.fromEpochSeconds(0))
        }
    }

    private val events = mutableListOf<String>()
    private val sessions = FakeSessions()
    private val databases = FakeDatabases(events)
    private val scheduler = FakeScheduler()
    private val syncEngine = FakeSyncEngine(events)
    private val userApi = FakeUserApi()

    private fun TestScope.session() =
        DefaultUserSession(sessions, userApi, databases, scheduler, syncEngine, backgroundScope, Logger.withTag("test"))
            .also { it.start() }

    private fun signedIn(userId: String? = null, refreshToken: String = "rt") =
        StoredSession(accessToken = "at", refreshToken = refreshToken, accessTokenExpiresAtEpochSeconds = 0, userId = userId)

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
    fun `đăng xuất thì dừng hẳn vòng sync đang chạy rồi mới xoá sổ của user hiện tại (B5)`() = runTest {
        val session = session()
        sessions.save(signedIn("user-a"))
        runCurrent()
        events.clear()

        session.wipeCurrentUser()

        assertEquals(listOf("stopSync", "delete:user-a"), events)
        assertEquals("cancel", scheduler.calls.last())
    }

    @Test
    fun `đổi sang user khác thì dừng sync và đóng sổ cũ trước khi mở sổ mới (B5)`() = runTest {
        session()
        sessions.save(signedIn("user-a"))
        runCurrent()
        events.clear()

        sessions.save(signedIn("user-b", refreshToken = "rt-b"))
        runCurrent()

        assertEquals(listOf("stopSync", "close", "open:user-b"), events)
    }

    @Test
    fun `phiên đổi trong lúc đang hỏi me thì userId của phiên cũ không được gắn vào phiên mới (B5)`() = runTest {
        session()
        userApi.whileInFlight = {
            // A đăng xuất, B đăng nhập đúng lúc /me của A đang bay; StateFlow có thể gộp mất bước đăng xuất.
            sessions.session.value = signedIn(refreshToken = "rt-b")
            userApi.whileInFlight = {}
            userApi.id = "user-b"
        }

        sessions.save(signedIn(refreshToken = "rt-a"))
        runCurrent()

        assertEquals("user-b", sessions.current()?.userId, "phiên B chỉ nhận userId từ /me của chính nó")
        assertTrue(databases.events.none { it == "open:user-a" }, "không bao giờ mở sổ của A bằng token của B")
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
