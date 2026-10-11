package com.uit.finance.core.datastore.secure

/**
 * Small encrypted key/value store for secrets (tokens, DB keys). Android: AES/GCM key in the Android
 * Keystore encrypting values in SharedPreferences. iOS: Keychain.
 * Synchronous by design; callers hop to the IO dispatcher.
 */
interface SecureStorage {
    fun putString(key: String, value: String)

    /**
     * `null` khi không có giá trị, hoặc giá trị **không bao giờ** đọc lại được nữa (khoá mã hoá đã bị huỷ;
     * khi đó giá trị cũng bị xoá luôn).
     *
     * @throws SecureStorageUnavailableException khi chỉ **tạm thời** không đọc được (Keystore bận, máy chưa
     *     mở khoá lần đầu sau khi bật). Giá trị vẫn còn nguyên; đừng coi như mất.
     */
    fun getString(key: String): String?

    fun remove(key: String)
    fun clear()
}

/** Kho bí mật tạm thời không đọc được. Thử lại sau, đừng xoá gì dựa trên lỗi này. */
class SecureStorageUnavailableException(cause: Throwable) : IllegalStateException("Secure storage is temporarily unavailable", cause)

/** Plain in-memory implementation for tests and previews. */
class InMemorySecureStorage : SecureStorage {
    private val values = mutableMapOf<String, String>()
    override fun putString(key: String, value: String) { values[key] = value }
    override fun getString(key: String): String? = values[key]
    override fun remove(key: String) { values.remove(key) }
    override fun clear() = values.clear()
}
