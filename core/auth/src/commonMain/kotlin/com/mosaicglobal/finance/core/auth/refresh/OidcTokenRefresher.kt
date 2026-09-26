package com.mosaicglobal.finance.core.auth.refresh

import co.touchlab.kermit.Logger
import com.mosaicglobal.finance.core.auth.client.OidcClient
import com.mosaicglobal.finance.core.auth.client.oidcCall
import com.mosaicglobal.finance.core.auth.client.toAuthTokens
import com.mosaicglobal.finance.core.common.result.AppError
import com.mosaicglobal.finance.core.common.result.AppResult
import com.mosaicglobal.finance.core.network.auth.RefreshOutcome
import com.mosaicglobal.finance.core.network.auth.TokenRefresher

/** Implement port [TokenRefresher] của core/network bằng token endpoint của Keycloak. */
internal class OidcTokenRefresher(
    private val client: OidcClient,
    private val logger: Logger,
) : TokenRefresher {

    override suspend fun refresh(refreshToken: String): RefreshOutcome =
        when (val result = oidcCall { client.refresh(refreshToken).toAuthTokens(fallbackRefreshToken = refreshToken) }) {
            is AppResult.Success -> RefreshOutcome.Refreshed(result.value)
            is AppResult.Failure -> result.error.toOutcome()
        }

    /**
     * Mọi 4xx từ token endpoint nghĩa là refresh token này không bao giờ dùng được nữa → Rejected.
     * Mạng / 5xx là tạm thời → Unavailable, giữ session.
     */
    private fun AppError.toOutcome(): RefreshOutcome = when {
        this == AppError.Unauthorized -> RefreshOutcome.Rejected
        this is AppError.Api && status in 400..499 -> RefreshOutcome.Rejected
        else -> RefreshOutcome.Unavailable.also { logger.w { "Refresh token tạm thất bại: $this" } }
    }
}
