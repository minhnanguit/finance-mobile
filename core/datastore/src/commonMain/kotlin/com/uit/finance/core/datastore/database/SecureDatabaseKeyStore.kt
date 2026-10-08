package com.uit.finance.core.datastore.database

import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.security.secureRandomBytes
import com.uit.finance.core.common.security.toHex
import com.uit.finance.core.database.DatabaseKeyStore
import com.uit.finance.core.datastore.secure.SecureStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Khoá 256-bit ngẫu nhiên cho DB của từng user, cất trong [SecureStorage] (Android Keystore / iOS
 * Keychain `AfterFirstUnlockThisDeviceOnly`), ADR-006 B6. Khoá không bao giờ ra khỏi máy.
 *
 * Kèm một danh mục các user đang có khoá, để biết máy này còn giữ sổ của ai.
 */
internal class SecureDatabaseKeyStore(
    private val secureStorage: SecureStorage,
    private val dispatchers: DispatcherProvider,
) : DatabaseKeyStore {

    private val mutex = Mutex()

    override suspend fun find(userId: String): String? = withContext(dispatchers.io) {
        secureStorage.getString(keyOf(userId))
    }

    override suspend fun create(userId: String): String = mutex.withLock {
        withContext(dispatchers.io) {
            val key = secureRandomBytes(KEY_BYTES).toHex()
            secureStorage.putString(keyOf(userId), key)
            writeIndex(readIndex() + userId)
            key
        }
    }

    override suspend fun delete(userId: String) = mutex.withLock {
        withContext(dispatchers.io) {
            secureStorage.remove(keyOf(userId))
            writeIndex(readIndex() - userId)
        }
    }

    override suspend fun userIds(): Set<String> = withContext(dispatchers.io) {
        readIndex().filterTo(mutableSetOf()) { secureStorage.getString(keyOf(it)) != null }
    }

    private fun readIndex(): Set<String> =
        secureStorage.getString(KEY_INDEX)?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet().orEmpty()

    private fun writeIndex(userIds: Set<String>) {
        if (userIds.isEmpty()) {
            secureStorage.remove(KEY_INDEX)
        } else {
            secureStorage.putString(KEY_INDEX, userIds.sorted().joinToString(SEPARATOR))
        }
    }

    private fun keyOf(userId: String) = "$KEY_PREFIX$userId"

    private companion object {
        const val KEY_BYTES = 32
        const val KEY_PREFIX = "db.key."
        const val KEY_INDEX = "db.users"
        const val SEPARATOR = ","
    }
}
