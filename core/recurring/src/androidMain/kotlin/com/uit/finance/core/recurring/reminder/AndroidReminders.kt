@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.recurring.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.recurring.runtime.RecurringCoordinator
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Hẹn thông báo bằng WorkManager (mỗi thông báo một OneTimeWork có độ trễ, tên cố định theo id kỳ): sống qua khởi động
 * lại máy, không cần quyền exact alarm; có thể trễ vài phút do Doze, chấp nhận (plan mục 6).
 */
internal class AndroidReminderScheduler(private val context: Context, private val clock: Clock) : ReminderScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun replaceAll(reminders: List<ReminderNotification>) {
        workManager.cancelAllWorkByTag(REMINDER_TAG)
        val now = clock.now()
        reminders.forEach { reminder ->
            val delayMs = (reminder.fireAt - now).inWholeMilliseconds.coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_ID to reminder.id,
                        KEY_BODY to ReminderPlanner.body(reminder),
                    ),
                )
                .addTag(REMINDER_TAG)
                .build()
            workManager.enqueueUniqueWork("$REMINDER_TAG.${reminder.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(REMINDER_TAG)
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
    }

    companion object {
        const val REMINDER_TAG = "finance.recurring.reminder"
        const val KEY_ID = "id"
        const val KEY_BODY = "body"
        const val CHANNEL_ID = "finance.recurring"
    }
}

/** Hiện một thông báo nhắc. Nội dung chỉ có tên + ngày (R15). Không có quyền thì im lặng bỏ qua. */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return Result.success()
        if (!notificationsAllowed(applicationContext)) return Result.success()
        manager.createNotificationChannel(
            NotificationChannel(AndroidReminderScheduler.CHANNEL_ID, "Khoản định kỳ", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val id = inputData.getString(AndroidReminderScheduler.KEY_ID) ?: return Result.success()
        val body = inputData.getString(AndroidReminderScheduler.KEY_BODY) ?: return Result.success()
        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val content = launch?.let {
            PendingIntent.getActivity(applicationContext, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = Notification.Builder(applicationContext, AndroidReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(ReminderPlanner.TITLE)
            .setContentText(body)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .apply { content?.let(::setContentIntent) }
            .build()
        manager.notify(id.hashCode(), notification)
        return Result.success()
    }
}

/** Worker hằng ngày: sinh kỳ khi app chạy nền mà sổ của user đang mở (không có thì bỏ qua an toàn). */
class RecurringGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
    private val coordinator: RecurringCoordinator,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        coordinator.runOnce()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "finance.recurring.daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringGenerationWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

internal fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Quyền `POST_NOTIFICATIONS` (Android 13+). Chỉ Activity xin được quyền, nên `MainActivity` gắn [launcher] lúc tạo;
 * chưa gắn (đang chạy nền) thì coi như chưa có quyền.
 */
class AndroidNotificationPermissionGate(private val context: Context) : NotificationPermissionGate {

    /** Gọi trình xin quyền của Activity, trả kết quả qua callback. */
    var launcher: ((onResult: (Boolean) -> Unit) -> Unit)? = null

    override fun isGranted(): Boolean = notificationsAllowed(context)

    override suspend fun request(): Boolean {
        if (isGranted()) return true
        val launch = launcher ?: return false
        return suspendCancellableCoroutine { continuation -> launch { granted -> if (continuation.isActive) continuation.resume(granted) } }
    }
}
