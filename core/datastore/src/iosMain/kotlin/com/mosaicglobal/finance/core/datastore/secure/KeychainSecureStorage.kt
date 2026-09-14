package com.mosaicglobal.finance.core.datastore.secure

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings

/** Keychain-backed storage (kSecClassGenericPassword items under one service name). */
@OptIn(ExperimentalSettingsImplementation::class)
internal class KeychainSecureStorage(
    private val keychain: KeychainSettings = KeychainSettings(service = KEYCHAIN_SERVICE),
) : SecureStorage {

    override fun putString(key: String, value: String) = keychain.putString(key, value)

    override fun getString(key: String): String? = keychain.getStringOrNull(key)

    override fun remove(key: String) = keychain.remove(key)

    override fun clear() = keychain.clear()

    private companion object {
        const val KEYCHAIN_SERVICE = "com.mosaicglobal.finance.secure"
    }
}
