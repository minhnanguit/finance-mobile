package com.mosaicglobal.finance.core.datastore.secure

/**
 * Small encrypted key/value store for secrets (tokens). Android: AES/GCM key in the Android
 * Keystore encrypting values in SharedPreferences. iOS: Keychain.
 * Synchronous by design; callers hop to the IO dispatcher.
 */
interface SecureStorage {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
    fun clear()
}

/** Plain in-memory implementation for tests and previews. */
class InMemorySecureStorage : SecureStorage {
    private val values = mutableMapOf<String, String>()
    override fun putString(key: String, value: String) { values[key] = value }
    override fun getString(key: String): String? = values[key]
    override fun remove(key: String) { values.remove(key) }
    override fun clear() = values.clear()
}
