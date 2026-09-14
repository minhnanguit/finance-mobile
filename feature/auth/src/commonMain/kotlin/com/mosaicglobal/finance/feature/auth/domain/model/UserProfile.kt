@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.feature.auth.domain.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: Instant,
)
