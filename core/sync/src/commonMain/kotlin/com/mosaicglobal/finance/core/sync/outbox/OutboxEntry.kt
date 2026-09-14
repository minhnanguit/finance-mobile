@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.core.sync.outbox

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class OutboxOp { CREATE, UPDATE, DELETE }

/** One pending local mutation. `payload` is the JSON body the sync endpoint expects. */
data class OutboxEntry(
    val id: String,
    val entity: String,
    val op: OutboxOp,
    val payload: String,
    val idempotencyKey: String,
    val createdAt: Instant,
    val attempts: Int = 0,
    val lastError: String? = null,
)
