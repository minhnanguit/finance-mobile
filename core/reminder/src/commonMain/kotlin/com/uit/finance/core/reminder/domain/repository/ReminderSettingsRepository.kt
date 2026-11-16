package com.uit.finance.core.reminder.domain.repository

import com.uit.finance.core.reminder.domain.model.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalTime

interface ReminderSettingsRepository {
    fun observe(): Flow<ReminderSettings>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setTime(time: LocalTime)
}
