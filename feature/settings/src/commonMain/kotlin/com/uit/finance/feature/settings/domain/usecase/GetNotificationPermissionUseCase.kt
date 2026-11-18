package com.uit.finance.feature.settings.domain.usecase

import com.uit.finance.core.notification.NotificationPermission

class GetNotificationPermissionUseCase(private val permission: NotificationPermission) {
    suspend operator fun invoke() = permission.status()
}
