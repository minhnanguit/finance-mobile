package com.uit.finance.core.datastore.session

import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.datastore.secure.SecureStorage
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.core.network.auth.TokenProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [SessionStore] on top of [SecureStorage]; also the [TokenProvider] the Ktor bearer plugin uses,
 * so token rotation performed by the network layer is persisted here transparently.
 * Storage is read lazily on first access, off the main thread.
 */
internal class SecureSessionStore(
    private val secureStorage: SecureStorage,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : SessionStore, TokenProvider {

    private val state = MutableStateFlow<StoredSession?>(null)
    private val mutex = Mutex()
    private var loaded = false

    override val session: Flow<StoredSession?> = flow {
        ensureLoaded()
        emitAll(state)
    }

    override suspend fun current(): StoredSession? {
        ensureLoaded()
        return state.value
    }

    override suspend fun save(session: StoredSession) = mutex.withLock {
        withContext(dispatchers.io) {
            secureStorage.putString(KEY_ACCESS_TOKEN, session.accessToken)
            secureStorage.putString(KEY_REFRESH_TOKEN, session.refreshToken)
            secureStorage.putString(KEY_EXPIRES_AT, session.accessTokenExpiresAtEpochSeconds.toString())
            session.userId?.let { secureStorage.putString(KEY_USER_ID, it) } ?: secureStorage.remove(KEY_USER_ID)
        }
        loaded = true
        state.value = session
    }

    override suspend fun attachUserId(userId: String) {
        val existing = current() ?: return
        save(existing.copy(userId = userId))
    }

    override suspend fun clear() = mutex.withLock {
        withContext(dispatchers.io) {
            listOf(KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN, KEY_EXPIRES_AT, KEY_USER_ID).forEach(secureStorage::remove)
        }
        loaded = true
        state.value = null
    }

    // ---- TokenProvider ----

    override suspend fun tokens(): AuthTokens? = current()?.let {
        AuthTokens(
            accessToken = it.accessToken,
            refreshToken = it.refreshToken,
            expiresInSeconds = (it.accessTokenExpiresAtEpochSeconds - clock.now().epochSeconds).toInt().coerceAtLeast(0),
        )
    }

    override suspend fun update(tokens: AuthTokens) {
        val existing = current()
        save(
            StoredSession(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                accessTokenExpiresAtEpochSeconds = clock.now().epochSeconds + tokens.expiresInSeconds,
                userId = existing?.userId,
            ),
        )
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            state.value = withContext(dispatchers.io) { read() }
            loaded = true
        }
    }

    private fun read(): StoredSession? {
        val access = secureStorage.getString(KEY_ACCESS_TOKEN) ?: return null
        val refresh = secureStorage.getString(KEY_REFRESH_TOKEN) ?: return null
        val expiresAt = secureStorage.getString(KEY_EXPIRES_AT)?.toLongOrNull() ?: 0L
        return StoredSession(
            accessToken = access,
            refreshToken = refresh,
            accessTokenExpiresAtEpochSeconds = expiresAt,
            userId = secureStorage.getString(KEY_USER_ID),
        )
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "session.access_token"
        const val KEY_REFRESH_TOKEN = "session.refresh_token"
        const val KEY_EXPIRES_AT = "session.expires_at"
        const val KEY_USER_ID = "session.user_id"
    }
}
