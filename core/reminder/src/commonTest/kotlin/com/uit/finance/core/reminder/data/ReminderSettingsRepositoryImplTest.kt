package com.uit.finance.core.reminder.data

import com.russhwolf.settings.Settings
import com.uit.finance.core.reminder.data.repository.ReminderSettingsRepositoryImpl
import com.uit.finance.core.reminder.domain.model.ReminderSettings
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.SessionNotice
import com.uit.finance.core.session.UserSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime

class ReminderSettingsRepositoryImplTest {

    private val store = MemorySettings()
    private val session = FakeUserSession()
    private val repository = ReminderSettingsRepositoryImpl(store, session)
    private val cleaner = ReminderUserDataCleaner(store)

    @Test
    fun `signed out reads the default and writes nothing`() = runTest {
        assertEquals(ReminderSettings.DEFAULT, repository.observe().first())
        repository.setEnabled(true)
        repository.setTime(LocalTime(7, 5))
        assertEquals(ReminderSettings.DEFAULT, repository.observe().first())
        assertTrue(store.keys.isEmpty())
    }

    @Test
    fun `two users do not share reminder keys`() = runTest {
        session.state.value = LocalDataState.Ready("user-a")
        repository.setEnabled(true)
        repository.setTime(LocalTime(19, 30))

        session.state.value = LocalDataState.Ready("user-b")
        assertEquals(ReminderSettings.DEFAULT, repository.observe().first())
        repository.setEnabled(true)
        repository.setTime(LocalTime(8, 15))

        session.state.value = LocalDataState.Ready("user-a")
        assertEquals(ReminderSettings(true, LocalTime(19, 30)), repository.observe().first())
        session.state.value = LocalDataState.Ready("user-b")
        assertEquals(ReminderSettings(true, LocalTime(8, 15)), repository.observe().first())
    }

    @Test
    fun `cleaner removes only the requested user`() = runTest {
        session.state.value = LocalDataState.Ready("user-a")
        repository.setEnabled(true)
        repository.setTime(LocalTime(19, 30))
        session.state.value = LocalDataState.Ready("user-b")
        repository.setEnabled(true)
        repository.setTime(LocalTime(8, 15))

        cleaner.clear("user-a")
        session.state.value = LocalDataState.Ready("user-a")
        assertEquals(ReminderSettings.DEFAULT, repository.observe().first())
        session.state.value = LocalDataState.Ready("user-b")
        assertEquals(ReminderSettings(true, LocalTime(8, 15)), repository.observe().first())
    }
}

private class MemorySettings : Settings {
    private val values = mutableMapOf<String, Any>()
    override val keys: Set<String> get() = values.keys
    override val size: Int get() = values.size
    override fun clear() = values.clear()
    override fun remove(key: String) { values.remove(key) }
    override fun hasKey(key: String) = key in values
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String) = values[key] as? Int
    override fun putLong(key: String, value: Long) { values[key] = value }
    override fun getLong(key: String, defaultValue: Long) = values[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String) = values[key] as? Long
    override fun putString(key: String, value: String) { values[key] = value }
    override fun getString(key: String, defaultValue: String) = values[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String) = values[key] as? String
    override fun putFloat(key: String, value: Float) { values[key] = value }
    override fun getFloat(key: String, defaultValue: Float) = values[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String) = values[key] as? Float
    override fun putDouble(key: String, value: Double) { values[key] = value }
    override fun getDouble(key: String, defaultValue: Double) = values[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String) = values[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String) = values[key] as? Boolean
}

private class FakeUserSession : UserSession {
    override val state = MutableStateFlow<LocalDataState>(LocalDataState.SignedOut)
    override val notice: StateFlow<SessionNotice?> = MutableStateFlow(null)
    override fun start() = Unit
    override fun refresh() = Unit
    override fun acknowledgeNotice() = Unit
    override suspend fun wipeCurrentUser() = Unit
    override suspend fun discardDeletedAccount() = Unit
    override suspend fun otherUsersOnDevice(currentUserId: String): Set<String> = emptySet()
    override suspend fun usersOnDevice(): Set<String> = emptySet()
    override suspend fun wipe(userIds: Set<String>) = Unit
}
