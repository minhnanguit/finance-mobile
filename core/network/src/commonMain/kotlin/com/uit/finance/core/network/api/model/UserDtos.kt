@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.network.api.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class UserProfileDto(
    /** Internal id của backend, không phải `sub` của Keycloak. */
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: Instant,
)
