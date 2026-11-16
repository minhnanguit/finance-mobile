package com.uit.finance.core.reminder.data

import com.russhwolf.settings.Settings
import com.uit.finance.core.reminder.data.repository.ReminderSettingsRepositoryImpl
import com.uit.finance.core.session.UserDataCleaner

internal class ReminderUserDataCleaner(private val settings: Settings) : UserDataCleaner {
    override suspend fun clear(userId: String) {
        settings.remove(ReminderSettingsRepositoryImpl.enabledKey(userId))
        settings.remove(ReminderSettingsRepositoryImpl.minuteKey(userId))
    }
}
