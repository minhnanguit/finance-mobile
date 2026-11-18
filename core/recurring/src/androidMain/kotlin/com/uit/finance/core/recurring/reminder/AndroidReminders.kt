package com.uit.finance.core.recurring.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uit.finance.core.recurring.runtime.RecurringCoordinator
import java.util.concurrent.TimeUnit

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
