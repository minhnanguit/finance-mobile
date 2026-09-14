package com.mosaicglobal.finance.core.sync.scheduler

import co.touchlab.kermit.Logger
import com.mosaicglobal.finance.core.common.coroutines.DispatcherProvider
import com.mosaicglobal.finance.core.common.result.fold
import com.mosaicglobal.finance.core.sync.engine.SyncEngine
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * BGTaskScheduler-based scheduler (stub level: registers + submits an app-refresh task).
 * Requirements on the Xcode side (already in iosApp/project.yml + Info.plist):
 *  - `UIBackgroundModes` contains `fetch`
 *  - `BGTaskSchedulerPermittedIdentifiers` contains [TASK_IDENTIFIER]
 *  - [registerLaunchHandler] must be called before the app finishes launching (done in `initKoin` on iOS).
 */
@OptIn(ExperimentalForeignApi::class) // BGTaskScheduler.submitTaskRequest takes a CPointer<NSError?> out-param
internal class BackgroundTaskSyncScheduler(
    private val syncEngine: SyncEngine,
    dispatchers: DispatcherProvider,
    private val logger: Logger,
) : SyncScheduler {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    fun registerLaunchHandler() {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = TASK_IDENTIFIER,
            usingQueue = null,
        ) { task ->
            val refreshTask = task as? BGAppRefreshTask ?: return@registerForTaskWithIdentifier
            schedulePeriodic() // re-arm: iOS only fires each request once
            val job = scope.launch {
                val success = syncEngine.sync().fold(onSuccess = { true }, onFailure = { false })
                refreshTask.setTaskCompletedWithSuccess(success)
            }
            refreshTask.expirationHandler = { job.cancel() }
        }
    }

    override fun schedulePeriodic() {
        val request = BGAppRefreshTaskRequest(identifier = TASK_IDENTIFIER).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(PERIOD_SECONDS)
        }
        val submitted = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        if (!submitted) logger.w { "BGTaskScheduler refused the sync request (simulator or missing entitlement?)" }
    }

    override fun requestImmediate() {
        // BGTaskScheduler has no "now"; run in-process while the app is foregrounded.
        scope.launch { syncEngine.sync() }
    }

    override fun cancelAll() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
    }

    companion object {
        const val TASK_IDENTIFIER = "com.mosaicglobal.finance.sync.refresh"
        private const val PERIOD_SECONDS = 15.0 * 60
    }
}
