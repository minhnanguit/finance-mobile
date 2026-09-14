package com.mosaicglobal.finance.core.network.api.model

enum class DevicePlatformDto { ANDROID, IOS }

data class DeviceInfoDto(
    val deviceId: String,
    val deviceName: String,
    val platform: DevicePlatformDto,
)

data class RegisterRequestDto(
    val email: String,
    val password: String,
    val displayName: String,
    val device: DeviceInfoDto,
)

data class LoginRequestDto(
    val email: String,
    val password: String,
    val device: DeviceInfoDto,
)

data class RefreshRequestDto(
    val refreshToken: String,
    val deviceId: String,
)

data class LogoutRequestDto(
    val refreshToken: String,
)

data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Int,
)
