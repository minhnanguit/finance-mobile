package com.mosaicglobal.finance.core.network.api.internal

import com.mosaicglobal.finance.core.common.id.UuidGenerator
import com.mosaicglobal.finance.core.network.api.AuthApi
import com.mosaicglobal.finance.core.network.api.UserApi
import com.mosaicglobal.finance.core.network.api.model.DeviceInfoDto
import com.mosaicglobal.finance.core.network.api.model.DevicePlatformDto
import com.mosaicglobal.finance.core.network.api.model.LoginRequestDto
import com.mosaicglobal.finance.core.network.api.model.LogoutRequestDto
import com.mosaicglobal.finance.core.network.api.model.RefreshRequestDto
import com.mosaicglobal.finance.core.network.api.model.RegisterRequestDto
import com.mosaicglobal.finance.core.network.api.model.TokenPairDto
import com.mosaicglobal.finance.core.network.api.model.UserProfileDto
import com.mosaicglobal.finance.core.network.generated.api.AuthApi as GeneratedAuthApi
import com.mosaicglobal.finance.core.network.generated.api.MeApi as GeneratedMeApi
import com.mosaicglobal.finance.core.network.generated.infrastructure.HttpResponse
import com.mosaicglobal.finance.core.network.generated.model.DeviceInfo
import com.mosaicglobal.finance.core.network.generated.model.LoginRequest
import com.mosaicglobal.finance.core.network.generated.model.LogoutRequest
import com.mosaicglobal.finance.core.network.generated.model.RefreshRequest
import com.mosaicglobal.finance.core.network.generated.model.RegisterRequest
import com.mosaicglobal.finance.core.network.generated.model.TokenPair
import com.mosaicglobal.finance.core.network.generated.model.UserProfile
import io.ktor.client.HttpClient

/*
 * The ONLY files allowed to touch the generated client (`...core.network.generated.*`).
 * They map between the generated wire models and the small DTOs the rest of the app sees, so a
 * regenerated client (new spec version) only ever affects this file.
 *
 * Non-2xx responses never reach `body()`: the shared HttpClient's response validator throws
 * ApiException first (see HttpClientFactory).
 */

internal class GeneratedAuthApiAdapter(
    baseUrl: String,
    httpClient: HttpClient,
    private val uuidGenerator: UuidGenerator,
) : AuthApi {

    private val delegate = GeneratedAuthApi(baseUrl = baseUrl, httpClient = httpClient)

    override suspend fun register(request: RegisterRequestDto): TokenPairDto =
        delegate.register(
            idempotencyKey = uuidGenerator.generate(),
            registerRequest = RegisterRequest(
                email = request.email,
                password = request.password,
                displayName = request.displayName,
                device = request.device.toGenerated(),
            ),
        ).body().toDto()

    override suspend fun login(request: LoginRequestDto): TokenPairDto =
        delegate.login(
            idempotencyKey = uuidGenerator.generate(),
            loginRequest = LoginRequest(
                email = request.email,
                password = request.password,
                device = request.device.toGenerated(),
            ),
        ).body().toDto()

    override suspend fun refresh(request: RefreshRequestDto): TokenPairDto =
        delegate.refresh(
            idempotencyKey = uuidGenerator.generate(),
            refreshRequest = RefreshRequest(refreshToken = request.refreshToken, deviceId = request.deviceId),
        ).body().toDto()

    override suspend fun logout(request: LogoutRequestDto) {
        delegate.logout(
            idempotencyKey = uuidGenerator.generate(),
            logoutRequest = LogoutRequest(refreshToken = request.refreshToken),
        ).requireSuccess()
    }
}

internal class GeneratedUserApiAdapter(
    baseUrl: String,
    httpClient: HttpClient,
) : UserApi {

    private val delegate = GeneratedMeApi(baseUrl = baseUrl, httpClient = httpClient)

    override suspend fun getCurrentUser(): UserProfileDto = delegate.getCurrentUser().body().toDto()
}

// ---- mappers (wire <-> network DTO) ----

private fun DeviceInfoDto.toGenerated() = DeviceInfo(
    deviceId = deviceId,
    deviceName = deviceName,
    platform = when (platform) {
        DevicePlatformDto.ANDROID -> DeviceInfo.Platform.ANDROID
        DevicePlatformDto.IOS -> DeviceInfo.Platform.IOS
    },
)

private fun TokenPair.toDto() = TokenPairDto(
    accessToken = accessToken,
    refreshToken = refreshToken,
    tokenType = tokenType,
    expiresInSeconds = expiresIn,
)

private fun UserProfile.toDto() = UserProfileDto(
    id = id,
    email = email,
    displayName = displayName,
    createdAt = createdAt,
)

/** 204 has no body; the validator already threw for anything non-2xx, this is a belt-and-braces check. */
private fun HttpResponse<Unit>.requireSuccess() {
    check(success) { "Unexpected HTTP $status" }
}
