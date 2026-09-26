@file:OptIn(ExperimentalTime::class)

package com.uit.finance.feature.auth.data.mapper

import com.uit.finance.core.auth.AuthorizationPrompt
import com.uit.finance.core.datastore.session.StoredSession
import com.uit.finance.core.network.api.model.UserProfileDto
import com.uit.finance.core.network.auth.AuthTokens
import com.uit.finance.feature.auth.domain.model.Session
import com.uit.finance.feature.auth.domain.model.SignInMode
import com.uit.finance.feature.auth.domain.model.UserProfile
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal fun SignInMode.toPrompt(): AuthorizationPrompt = when (this) {
    SignInMode.SignIn -> AuthorizationPrompt.SignIn
    SignInMode.SignUp -> AuthorizationPrompt.SignUp
}

internal fun AuthTokens.toStoredSession(now: Instant): StoredSession = StoredSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessTokenExpiresAtEpochSeconds = now.epochSeconds + expiresInSeconds,
    userId = null,
)

internal fun StoredSession.toDomain(): Session = Session(
    userId = userId,
    accessTokenExpiresAt = Instant.fromEpochSeconds(accessTokenExpiresAtEpochSeconds),
)

internal fun UserProfileDto.toDomain(): UserProfile = UserProfile(
    id = id,
    email = email,
    displayName = displayName,
    createdAt = createdAt,
)
