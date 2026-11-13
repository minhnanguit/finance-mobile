package com.uit.finance.feature.settings.data.mapper

import com.uit.finance.core.auth.TokenProfile
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.network.api.ApiErrorCodes
import com.uit.finance.core.network.api.model.UserProfileDto
import com.uit.finance.feature.settings.domain.model.AccountProfile

internal fun UserProfileDto.toDomain(): AccountProfile = AccountProfile(
    displayName = displayName,
    email = email,
    memberSince = createdAt,
)

internal fun TokenProfile.toDomain(): AccountProfile = AccountProfile(
    displayName = displayName ?: email ?: "",
    email = email,
    memberSince = null,
)

/** Tài khoản đã bị xoá (có thể từ máy khác, hoặc lần gọi trước đã thành công): coi như đã xoá xong. */
internal fun AppError.isUserDeleted(): Boolean =
    this is AppError.Api && status == HTTP_FORBIDDEN && code == ApiErrorCodes.USER_DELETED

/** Lần đăng nhập lại đã quá 5 phút khi tới server (A2). */
internal fun AppError.isReauthRequired(): Boolean =
    this is AppError.Api && status == HTTP_FORBIDDEN && code == ApiErrorCodes.REAUTH_REQUIRED

/**
 * Không biết request đã được xử lý hay chưa: mất kết nối / hết giờ chờ (có thể server đã commit rồi mới đứt), lỗi bất
 * ngờ phía client sau khi đã gửi, hoặc server lỗi 5xx. Lỗi 4xx thì chắc chắn chưa xử lý.
 */
internal fun AppError.isOutcomeUnknown(): Boolean =
    this is AppError.Network || this is AppError.Unknown || (this is AppError.Api && status >= HTTP_SERVER_ERROR)

private const val HTTP_FORBIDDEN = 403
private const val HTTP_SERVER_ERROR = 500
