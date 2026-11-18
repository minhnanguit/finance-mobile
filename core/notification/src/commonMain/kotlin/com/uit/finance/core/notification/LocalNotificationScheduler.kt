package com.uit.finance.core.notification

interface LocalNotificationScheduler {
    /** Replaces every pending notification in [group]. Empty means cancel all. */
    suspend fun replaceGroup(group: String, notifications: List<LocalNotification>)

    /** Removes notifications from [group] that are already visible in the system tray. */
    suspend fun clearDelivered(group: String)
}
