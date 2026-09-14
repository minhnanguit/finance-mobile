package com.mosaicglobal.finance.core.sync.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.fold
import com.mosaicglobal.finance.core.sync.engine.SyncEngine

/** Constructed by Koin's `WorkerFactory` (see `workerOf(::SyncWorker)` in the platform module). */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = syncEngine.sync().fold(
        onSuccess = { Result.success() },
        onFailure = { error ->
            when {
                error is AppError.Unauthorized -> Result.failure()
                runAttemptCount < MAX_RETRIES -> Result.retry()
                else -> Result.failure()
            }
        },
    )

    private companion object {
        const val MAX_RETRIES = 3
    }
}
