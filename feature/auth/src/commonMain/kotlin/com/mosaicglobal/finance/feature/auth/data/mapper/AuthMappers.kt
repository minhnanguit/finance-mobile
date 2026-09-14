@file:OptIn(ExperimentalTime::class)

package com.mosaicglobal.finance.feature.auth.data.mapper

import com.mosaicglobal.finance.core.common.platform.Platform
import com.mosaicglobal.finance.core.datastore.session.StoredSession
import com.mosaicglobal.finance.core.network.api.model.DeviceInfoDto
import com.mosaicglobal.finance.core.network.api.model.DevicePlatformDto
import com.mosaicglobal.finance.core.network.api.model.TokenPairDto
import com.mosaicglobal.finance.core.network.api.model.UserProfileDto
import com.mosaicglobal.finance.feature.auth.domain.model.DeviceInfo
import com.mosaicglobal.finance.feature.auth.domain.model.Session
import com.mosaicglobal.finance.feature.auth.domain.model.UserProfile
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal fun DeviceInfo.toDto(): DeviceInfoDto = DeviceInfoDto(
    deviceId = deviceId,
    deviceName = deviceName,
    platform = when (platform) {
        Platform.ANDROID -> DevicePlatformDto.ANDROID
        Platform.IOS -> DevicePlatformDto.IOS
    },
)

internal fun TokenPairDto.toStoredSession(now: Instant, userId: String?): StoredSession = StoredSession(
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessTokenExpiresAtEpochSeconds = now.epochSeconds + expiresInSeconds,
    userId = userId,
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
