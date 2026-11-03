package com.uit.finance.core.auth

/**
 * @param issuer phải trùng TUYỆT ĐỐI claim `iss` mà Keycloak ghi vào token (không có `/` cuối).
 * @param redirectUri phải khớp `redirectUris` của client trong realm, và intent-filter (Android).
 * @param uiLocales danh sách locale ưu tiên gửi tới Keycloak theo OIDC, locale đầu tiên được ưu tiên.
 */
data class OidcConfig(
    val issuer: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String> = listOf("openid", "profile", "email"),
    val uiLocales: List<String> = emptyList(),
) {
    init {
        require(issuer.startsWith("http://") || issuer.startsWith("https://")) { "issuer phải là URL tuyệt đối: $issuer" }
        require(!issuer.endsWith("/")) { "issuer không được có '/' cuối: $issuer" }
        require("openid" in scopes) { "scope phải có 'openid'" }
        require(uiLocales.none(String::isBlank)) { "uiLocales không được chứa locale rỗng" }
    }
}

/** Orders the supported Keycloak locales from the user's current app/device language. */
fun preferredUiLocales(languageCode: String): List<String> {
    val preferred = languageCode.substringBefore('-').substringBefore('_').lowercase()
    val supported = listOf("vi", "en")
    return if (preferred in supported) listOf(preferred) + supported.filterNot { it == preferred } else supported.reversed()
}
