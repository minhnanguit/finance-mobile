package com.uit.finance.core.reminder.data.repository

import com.russhwolf.settings.Settings
import com.uit.finance.core.reminder.domain.model.ReminderSettings
import com.uit.finance.core.reminder.domain.repository.ReminderSettingsRepository
import com.uit.finance.core.session.LocalDataState
import com.uit.finance.core.session.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalTime

internal class ReminderSettingsRepositoryImpl(
    private val settings: Settings,
    private val session: UserSession,
) : ReminderSettingsRepository {
    private val version = MutableStateFlow(0L)

    override fun observe(): Flow<ReminderSettings> = combine(session.state, version) { state, _ ->
        val userId = (state as? LocalDataState.Ready)?.userId ?: return@combine ReminderSettings.DEFAULT
        ReminderSettings(
            enabled = settings.getBoolean(enabledKey(userId), false),
            time = LocalTime.fromSecondOfDay(settings.getInt(minuteKey(userId), DEFAULT_MINUTE) * 60),
        )
    }.distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean) {
        val userId = (session.state.value as? LocalDataState.Ready)?.userId ?: return
        settings.putBoolean(enabledKey(userId), enabled)
        version.value += 1
    }

    override suspend fun setTime(time: LocalTime) {
        val userId = (session.state.value as? LocalDataState.Ready)?.userId ?: return
        settings.putInt(minuteKey(userId), time.hour * 60 + time.minute)
        version.value += 1
    }

    internal companion object {
        const val DEFAULT_MINUTE = 21 * 60
        fun enabledKey(userId: String) = "reminder.$userId.enabled"
        fun minuteKey(userId: String) = "reminder.$userId.minute_of_day"
    }
}
