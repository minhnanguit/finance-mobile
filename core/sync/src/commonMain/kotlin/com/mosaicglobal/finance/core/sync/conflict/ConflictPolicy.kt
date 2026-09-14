@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.core.sync.conflict

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Minimal shape every synced entity has (ARCHITECTURE.md §6.3 item 4). */
interface Syncable {
    val id: String
    val version: Long
    val updatedAt: Instant
    val deletedAt: Instant?
}

sealed interface Resolution<out T> {
    data class KeepLocal<T>(val value: T) : Resolution<T>
    data class TakeRemote<T>(val value: T) : Resolution<T>
}

interface ConflictPolicy {
    fun <T : Syncable> resolve(local: T, remote: T): Resolution<T>
}

/**
 * Last-write-wins keyed on the server's `updatedAt`. Ties go to the remote copy because the
 * server timestamp is authoritative and the local copy will be re-pulled anyway.
 */
object LastWriteWinsPolicy : ConflictPolicy {
    override fun <T : Syncable> resolve(local: T, remote: T): Resolution<T> =
        if (local.updatedAt > remote.updatedAt) Resolution.KeepLocal(local) else Resolution.TakeRemote(remote)
}
