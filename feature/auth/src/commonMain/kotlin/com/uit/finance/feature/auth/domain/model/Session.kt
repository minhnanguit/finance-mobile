@file:OptIn(ExperimentalTime::class)

package com.uit.finance.feature.auth.domain.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Session đang mở trên máy này. `userId` (internal id) có sau khi đã gọi profile. */
data class Session(
    val userId: String?,
    val accessTokenExpiresAt: Instant,
)
