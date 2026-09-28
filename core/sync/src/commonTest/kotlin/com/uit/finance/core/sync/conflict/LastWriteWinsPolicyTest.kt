@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.sync.conflict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class LastWriteWinsPolicyTest {

    private data class Note(
        override val id: String,
        override val version: Long,
        override val updatedAt: Instant,
        override val deletedAt: Instant? = null,
        val text: String,
    ) : Syncable

    private val t0 = Instant.fromEpochSeconds(1_000)

    @Test
    fun newerLocalWins() {
        val local = Note("1", 2, t0 + kotlin.time.Duration.parse("1s"), text = "local")
        val remote = Note("1", 3, t0, text = "remote")
        assertEquals(Resolution.KeepLocal(local), LastWriteWinsPolicy.resolve(local, remote))
    }

    @Test
    fun newerRemoteWins() {
        val local = Note("1", 2, t0, text = "local")
        val remote = Note("1", 3, t0 + kotlin.time.Duration.parse("1s"), text = "remote")
        assertEquals(Resolution.TakeRemote(remote), LastWriteWinsPolicy.resolve(local, remote))
    }

    @Test
    fun tieGoesToServer() {
        val local = Note("1", 2, t0, text = "local")
        val remote = Note("1", 2, t0, text = "remote")
        assertEquals(Resolution.TakeRemote(remote), LastWriteWinsPolicy.resolve(local, remote))
    }
}
