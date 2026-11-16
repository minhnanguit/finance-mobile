package com.uit.finance.core.notification

import kotlin.time.Instant

data class LocalNotification(
    val id: String,
    val group: String,
    val fireAt: Instant,
    val title: String,
    val body: String,
    val action: String,
)
