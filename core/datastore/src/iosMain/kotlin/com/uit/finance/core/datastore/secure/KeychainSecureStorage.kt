package com.uit.finance.core.datastore.secure

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.CFBridgingRetain
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService

/**
 * Keychain-backed storage (kSecClassGenericPassword items under one service name).
 *
 * `AfterFirstUnlockThisDeviceOnly` (ADR-006 B6): sync nền vẫn đọc được token và khoá DB khi máy đang
 * khoá màn hình (sau lần mở khoá đầu tiên), và item không theo backup iCloud sang máy khác.
 */
@OptIn(ExperimentalSettingsImplementation::class, ExperimentalForeignApi::class)
internal class KeychainSecureStorage(
    private val keychain: KeychainSettings = KeychainSettings(
        kSecAttrService to CFBridgingRetain(KEYCHAIN_SERVICE),
        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
    ),
) : SecureStorage {

    override fun putString(key: String, value: String) = keychain.putString(key, value)

    // KeychainSettings chỉ trả null khi errSecItemNotFound; mọi OSStatus khác (vd errSecInteractionNotAllowed
    // trước lần mở khoá đầu tiên) là IllegalStateException: item vẫn còn, chỉ là chưa đọc được lúc này.
    override fun getString(key: String): String? = try {
        keychain.getStringOrNull(key)
    } catch (e: IllegalStateException) {
        throw SecureStorageUnavailableException(e)
    }

    override fun remove(key: String) = keychain.remove(key)

    override fun clear() = keychain.clear()

    private companion object {
        const val KEYCHAIN_SERVICE = "com.uit.finance.secure"
    }
}
