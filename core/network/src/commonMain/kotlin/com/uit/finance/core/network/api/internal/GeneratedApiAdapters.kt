package com.uit.finance.core.network.api.internal

import com.uit.finance.core.network.api.UserApi
import com.uit.finance.core.network.api.model.UserProfileDto
import com.uit.finance.core.network.generated.api.MeApi as GeneratedMeApi
import com.uit.finance.core.network.generated.model.UserProfile
import io.ktor.client.HttpClient

/*
 * File DUY NHẤT được import generated client (`...core.network.generated.*`). Nó map giữa wire model
 * generated và DTO nhỏ mà phần còn lại của app thấy, nên generate lại client (spec mới) chỉ ảnh hưởng
 * file này.
 *
 * Response non-2xx không bao giờ tới `body()`: response validator của HttpClient dùng chung đã throw
 * ApiException trước (xem HttpClientFactory).
 */

internal class GeneratedUserApiAdapter(
    baseUrl: String,
    httpClient: HttpClient,
) : UserApi {

    private val delegate = GeneratedMeApi(baseUrl = baseUrl, httpClient = httpClient)

    override suspend fun getCurrentUser(): UserProfileDto = delegate.getCurrentUser().body().toDto()
}

private fun UserProfile.toDto() = UserProfileDto(
    id = id,
    email = email,
    displayName = displayName,
    createdAt = createdAt,
)
