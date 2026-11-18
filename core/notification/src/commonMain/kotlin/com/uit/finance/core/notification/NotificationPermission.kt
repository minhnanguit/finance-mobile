package com.uit.finance.core.notification

enum class NotificationPermissionStatus { Granted, Denied }

interface NotificationPermission {
    suspend fun status(): NotificationPermissionStatus
    suspend fun request(): NotificationPermissionStatus
    fun openSystemSettings()
}
