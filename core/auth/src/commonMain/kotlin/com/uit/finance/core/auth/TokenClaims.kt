package com.uit.finance.core.auth

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Thông tin hiển thị lấy từ token đang giữ, dùng được cả khi offline. */
data class TokenProfile(
    val subject: String,
    val email: String?,
    val displayName: String?,
)

/**
 * Đọc claim của access token Keycloak (JWT). **Không kiểm chữ ký**: chỉ dùng để hiển thị và để so hai
 * token có cùng `sub` không; quyền truy cập luôn do backend quyết định sau khi verify chữ ký.
 *
 * Token không phải JWT (Keycloak bật "lightweight access token") thì trả `null`: bên gọi phải coi như
 * không biết, không đoán.
 */
object TokenClaims {

    private val json = Json { ignoreUnknownKeys = true }
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun profile(accessToken: String): TokenProfile? {
        val claims = payload(accessToken) ?: return null
        val subject = claims.text("sub") ?: return null
        return TokenProfile(
            subject = subject,
            email = claims.text("email") ?: claims.text("preferred_username"),
            displayName = claims.text("name") ?: claims.text("preferred_username"),
        )
    }

    fun subject(accessToken: String): String? = payload(accessToken)?.text("sub")

    private fun payload(token: String): JsonObject? = try {
        val parts = token.split('.')
        if (parts.size != JWT_PARTS) {
            null
        } else {
            json.parseToJsonElement(base64Url.decode(parts[1]).decodeToString()).jsonObject
        }
    } catch (e: IllegalArgumentException) {
        // Base64 hỏng, JSON hỏng hoặc không phải object (SerializationException là IllegalArgumentException).
        null
    }

    private fun JsonObject.text(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private const val JWT_PARTS = 3
}
