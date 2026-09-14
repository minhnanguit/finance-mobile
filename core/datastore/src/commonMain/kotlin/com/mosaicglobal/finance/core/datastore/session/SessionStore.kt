package com.mosaicglobal.finance.core.datastore.session

import kotlinx.coroutines.flow.Flow

/** What we persist about the signed-in user. Tokens live in secure storage only. */
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAtEpochSeconds: Long,
    val userId: String? = null,
)

interface SessionStore {
    /** Emits the current session and every change; `null` means signed out. */
    val session: Flow<StoredSession?>
    suspend fun current(): StoredSession?
    suspend fun save(session: StoredSession)
    suspend fun attachUserId(userId: String)
    suspend fun clear()
}
