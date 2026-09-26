@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.feature.auth.data.mapper

import com.mosaicglobal.finance.core.auth.AuthorizationPrompt
import com.mosaicglobal.finance.core.datastore.session.StoredSession
import com.mosaicglobal.finance.core.network.api.model.UserProfileDto
import com.mosaicglobal.finance.core.network.auth.AuthTokens
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.model.SignInMode
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
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
