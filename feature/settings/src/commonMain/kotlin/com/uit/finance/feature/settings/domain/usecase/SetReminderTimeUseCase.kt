package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.core.reminder.domain.repository.ReminderSettingsRepository
import kotlinx.datetime.LocalTime

class SetReminderTimeUseCase(private val repository: ReminderSettingsRepository) {
    suspend operator fun invoke(hour: Int, minute: Int) = repository.setTime(LocalTime(hour, minute))
}
