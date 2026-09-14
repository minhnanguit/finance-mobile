@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.feature.auth.domain.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** An open session on this device. `userId` is known once the profile has been fetched. */
data class Session(
    val userId: String?,
    val accessTokenExpiresAt: Instant,
)
