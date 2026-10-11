package com.uit.finance.core.datastore.session

import app.cash.turbine.test
import com.uit.finance.core.datastore.secure.InMemorySecureStorage
import com.uit.finance.core.datastore.secure.FlakySecureStorage
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureSessionStoreTest {

    private val storage = InMemorySecureStorage()
    private val clock = TestClock()

    private fun TestScope.store() = SecureSessionStore(storage, testDispatcherProvider(), clock)

    @Test
    fun persistsAndReloadsSession() = runTest {
        val session = StoredSession("access", "refresh", 1_700_000_900L, userId = "u1")
        store().save(session)

        // A fresh instance must read what the previous one wrote.
        assertEquals(session, store().current())
    }

    @Test
    fun tokenProviderRotationKeepsUserId() = runTest {
        val store = store()
        store.save(StoredSession("a1", "r1", clock.now().epochSeconds + 900, userId = "u1"))

        assertTrue(store.replaceIfCurrent(refreshedFrom = "r1", tokens = AuthTokens("a2", "r2", expiresInSeconds = 900)))

        val current = store.current()
        assertEquals("a2", current?.accessToken)
        assertEquals("r2", current?.refreshToken)
        assertEquals("u1", current?.userId)
        assertEquals(clock.now().epochSeconds + 900, current?.accessTokenExpiresAtEpochSeconds)
    }

    @Test
    fun staleRefreshNeverOverwritesOrRevivesAnotherSession() = runTest {
        val store = store()
        val sessionOfB = StoredSession("b", "rb", 0L, userId = "user-b")
        store.save(sessionOfB)

        // Refresh bắt đầu từ phiên A (refresh token "ra") về muộn, sau khi B đã đăng nhập.
        assertFalse(store.replaceIfCurrent(refreshedFrom = "ra", tokens = AuthTokens("a2", "ra2", 900)))
        assertFalse(store.clearIfCurrent(rejectedRefreshToken = "ra"))
        assertEquals(sessionOfB, store.current())

        // A đã đăng xuất hẳn: refresh muộn của A cũng không được "hồi sinh" phiên.
        store.clear()
        assertFalse(store.replaceIfCurrent(refreshedFrom = "ra", tokens = AuthTokens("a2", "ra2", 900)))
        assertNull(store.current())
    }

    @Test
    fun attachUserIdOnlyToTheSessionThatAskedMe() = runTest {
        val store = store()
        store.save(StoredSession("b", "rb", 0L))

        assertFalse(store.attachUserId("user-a", forRefreshToken = "ra"))
        assertNull(store.current()?.userId)
        assertTrue(store.attachUserId("user-b", forRefreshToken = "rb"))
        assertEquals("user-b", store.current()?.userId)
    }

    @Test
    fun unreadableStorageIsNotTreatedAsSignedOutForever() = runTest {
        val flaky = FlakySecureStorage(storage)
        storage.putString("session.access_token", "a")
        storage.putString("session.refresh_token", "r")
        val store = SecureSessionStore(flaky, testDispatcherProvider(), clock)

        flaky.unavailable = true
        assertNull(store.current(), "tạm thời không đọc được: coi như chưa đăng nhập lúc này")
        flaky.unavailable = false

        assertEquals("r", store.current()?.refreshToken, "đọc lại được thì phiên vẫn còn nguyên")
        assertEquals("a", storage.getString("session.access_token"), "không xoá gì vì lỗi tạm thời")
    }

    @Test
    fun clearEmitsNullAndWipesStorage() = runTest {
        val store = store()
        store.save(StoredSession("a", "r", 0L))
        store.session.test {
            assertEquals("a", awaitItem()?.accessToken)
            store.clear()
            assertNull(awaitItem())
        }
        assertNull(storage.getString("session.access_token"))
        assertNull(store.tokens())
    }
}
