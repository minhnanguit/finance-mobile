package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.core.notification.NotificationPermission

class OpenNotificationSettingsUseCase(private val permission: NotificationPermission) {
    operator fun invoke() = permission.openSystemSettings()
}
