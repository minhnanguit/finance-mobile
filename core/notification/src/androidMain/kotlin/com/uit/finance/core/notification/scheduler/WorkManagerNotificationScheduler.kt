package com.uit.finance.core.notification.scheduler

import android.content.Context
import android.app.NotificationManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.uit.finance.core.common.coroutines.DispatcherProvider
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.notification.LocalNotification
import com.uit.finance.core.notification.LocalNotificationScheduler
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlinx.coroutines.withContext

internal class WorkManagerNotificationScheduler(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val clock: Clock,
) : LocalNotificationScheduler {
    private val workManager = WorkManager.getInstance(context)

    override suspend fun replaceGroup(group: String, notifications: List<LocalNotification>) {
        withContext(dispatchers.io) {
            workManager.cancelAllWorkByTag(group).result.get()
            val now = clock.now().toEpochMilliseconds()
            notifications.forEach { notification ->
                val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                    .setInitialDelay(max(0L, notification.fireAt.toEpochMilliseconds() - now), TimeUnit.MILLISECONDS)
                    .setInputData(notification.toData())
                    .addTag(group)
                    .build()
                workManager.enqueueUniqueWork(notification.id, ExistingWorkPolicy.REPLACE, request).result.get()
            }
        }
    }

    override suspend fun clearDelivered(group: String) {
        context.getSystemService(NotificationManager::class.java).cancel(group.hashCode())
    }

    private fun LocalNotification.toData(): Data = Data.Builder()
        .putString(NotificationWorker.KEY_ID, id)
        .putString(NotificationWorker.KEY_GROUP, group)
        .putLong(NotificationWorker.KEY_FIRE_AT, fireAt.toEpochMilliseconds())
        .putString(NotificationWorker.KEY_TITLE, title)
        .putString(NotificationWorker.KEY_BODY, body)
        .putString(NotificationWorker.KEY_ACTION, action)
        .build()
}
