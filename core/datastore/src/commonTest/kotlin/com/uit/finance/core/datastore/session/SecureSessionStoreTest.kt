package com.uit.finance.core.datastore.session

import app.cash.turbine.test
import com.uit.finance.core.datastore.secure.InMemorySecureStorage
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.core.testing.TestClock
import com.uit.finance.core.testing.testDispatcherProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        store.update(AuthTokens("a2", "r2", expiresInSeconds = 900))

        val current = store.current()
        assertEquals("a2", current?.accessToken)
        assertEquals("r2", current?.refreshToken)
        assertEquals("u1", current?.userId)
        assertEquals(clock.now().epochSeconds + 900, current?.accessTokenExpiresAtEpochSeconds)
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
