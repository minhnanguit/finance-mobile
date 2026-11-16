package com.uit.finance.core.notification.permission

import com.uit.finance.core.notification.NotificationPermissionStatus
import kotlinx.coroutines.CompletableDeferred

internal object PermissionRequestBridge {
    private val lock = Any()
    private var pending: CompletableDeferred<NotificationPermissionStatus>? = null

    suspend fun run(start: () -> Unit): NotificationPermissionStatus {
        val deferred = CompletableDeferred<NotificationPermissionStatus>()
        synchronized(lock) {
            pending?.complete(NotificationPermissionStatus.Denied)
            pending = deferred
        }
        return try {
            start()
            deferred.await()
        } finally {
            synchronized(lock) { if (pending === deferred) pending = null }
        }
    }

    fun complete(status: NotificationPermissionStatus) {
        synchronized(lock) {
            pending?.complete(status)
            pending = null
        }
    }
}
