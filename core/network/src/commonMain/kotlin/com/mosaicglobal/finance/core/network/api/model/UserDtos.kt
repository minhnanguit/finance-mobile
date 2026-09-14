@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.core.network.api.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class UserProfileDto(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: Instant,
)
