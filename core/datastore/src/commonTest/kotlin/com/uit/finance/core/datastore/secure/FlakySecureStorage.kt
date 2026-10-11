package com.uit.finance.core.datastore.secure

/** Keystore/Keychain tạm bận: khi [unavailable], mọi lần đọc ném [SecureStorageUnavailableException]. */
internal class FlakySecureStorage(private val delegate: SecureStorage = InMemorySecureStorage()) : SecureStorage by delegate {
    var unavailable = false

    override fun getString(key: String): String? {
        if (unavailable) throw SecureStorageUnavailableException(IllegalStateException("keystore busy"))
        return delegate.getString(key)
    }
}
