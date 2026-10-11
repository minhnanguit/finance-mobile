package com.uit.finance.core.datastore.database

import com.uit.finance.core.datastore.secure.FlakySecureStorage
import com.uit.finance.core.datastore.secure.SecureStorageUnavailableException
import com.uit.finance.core.testing.testDispatcherProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class SecureDatabaseKeyStoreTest {

    private val storage = FlakySecureStorage()

    private fun TestScope.keys() = SecureDatabaseKeyStore(storage, testDispatcherProvider())

    @Test
    fun `khoá 256-bit mới cho mỗi user, đọc lại đúng khoá đó`() = runTest {
        val keys = keys()

        val key = keys.create("user-a")

        assertEquals(64, key.length, "32 byte dạng hex")
        assertEquals(key, keys.find("user-a"))
        assertNull(keys.find("user-b"))
    }

    @Test
    fun `Keystore tạm không đọc được thì báo lỗi chứ không trả null - DB không bị xoá oan`() = runTest {
        val keys = keys()
        keys.create("user-a")
        storage.unavailable = true

        assertFailsWith<SecureStorageUnavailableException> { keys.find("user-a") }

        storage.unavailable = false
        assertNotNull(keys.find("user-a"), "khoá vẫn còn nguyên")
    }

    @Test
    fun `danh sách user trên máy không ném lỗi khi Keystore tạm bận`() = runTest {
        val keys = keys()
        keys.create("user-a")
        storage.unavailable = true

        assertEquals(emptySet(), keys.userIds())
    }
}
