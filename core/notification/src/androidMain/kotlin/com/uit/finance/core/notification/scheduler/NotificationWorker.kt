package com.uit.finance.core.notification.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uit.finance.core.notification.R
import com.uit.finance.core.notification.tap.EXTRA_NOTIFICATION_ACTION
import kotlin.time.Duration.Companion.hours

class NotificationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val fireAt = inputData.getLong(KEY_FIRE_AT, 0L)
        if (fireAt == 0L || System.currentTimeMillis() - fireAt > MAX_LATENESS.inWholeMilliseconds) return Result.success()
        val group = inputData.getString(KEY_GROUP) ?: return Result.failure()
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        ensureChannel(applicationContext)
        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            ?: return Result.failure()
        launch.action = null
        launch.categories?.toList()?.forEach(launch::removeCategory)
        launch.putExtra(EXTRA_NOTIFICATION_ACTION, action)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setGroup(group)
            .setContentIntent(pendingIntent)
            .build()
        manager(applicationContext).notify(group.hashCode(), notification)
        return Result.success()
    }

    companion object {
        internal const val KEY_ID = "notification.id"
        internal const val KEY_GROUP = "notification.group"
        internal const val KEY_FIRE_AT = "notification.fire_at"
        internal const val KEY_TITLE = "notification.title"
        internal const val KEY_BODY = "notification.body"
        internal const val KEY_ACTION = "notification.action"
        private const val CHANNEL_ID = "reminders"
        private val MAX_LATENESS = 3.hours
        private fun manager(context: Context): NotificationManager {
            return context.getSystemService(NotificationManager::class.java)
        }

        private fun ensureChannel(context: Context) {
            manager(context).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nhắc nhở", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
