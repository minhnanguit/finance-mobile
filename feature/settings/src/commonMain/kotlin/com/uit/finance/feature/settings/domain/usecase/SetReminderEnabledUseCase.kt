package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.core.notification.NotificationPermission
import com.uit.finance.core.notification.NotificationPermissionStatus
import com.uit.finance.core.reminder.domain.repository.ReminderSettingsRepository
import com.uit.finance.feature.settings.domain.model.ReminderToggleResult

class SetReminderEnabledUseCase(
    private val repository: ReminderSettingsRepository,
    private val permission: NotificationPermission,
) {
    suspend operator fun invoke(enabled: Boolean): ReminderToggleResult {
        if (enabled && permission.request() == NotificationPermissionStatus.Denied) {
            return ReminderToggleResult.PermissionDenied
        }
        repository.setEnabled(enabled)
        return ReminderToggleResult.Saved
    }
}
