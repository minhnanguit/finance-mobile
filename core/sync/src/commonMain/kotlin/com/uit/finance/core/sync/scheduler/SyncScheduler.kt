package com.uit.finance.core.sync.scheduler

/** Background execution of [com.uit.finance.core.sync.engine.SyncEngine]: WorkManager / BGTaskScheduler. */
interface SyncScheduler {
    /** Periodic best-effort sync (network required). Idempotent. */
    fun schedulePeriodic()
    /** One-shot sync as soon as constraints allow (e.g. right after a local write). */
    fun requestImmediate()
    fun cancelAll()
}

const val SYNC_PERIODIC_WORK_NAME: String = "finance.sync.periodic"
const val SYNC_IMMEDIATE_WORK_NAME: String = "finance.sync.immediate"
