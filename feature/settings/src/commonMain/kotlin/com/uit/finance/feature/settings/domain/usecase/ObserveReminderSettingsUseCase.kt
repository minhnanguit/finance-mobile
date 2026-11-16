package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.core.reminder.domain.repository.ReminderSettingsRepository

class ObserveReminderSettingsUseCase(private val repository: ReminderSettingsRepository) {
    operator fun invoke() = repository.observe()
}
