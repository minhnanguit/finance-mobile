package com.uit.finance.core.auth

/**
 * @param issuer phải trùng TUYỆT ĐỐI claim `iss` mà Keycloak ghi vào token (không có `/` cuối).
 * @param redirectUri phải khớp `redirectUris` của client trong realm, và intent-filter (Android).
 */
data class OidcConfig(
    val issuer: String,
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String> = listOf("openid", "profile", "email"),
) {
    init {
        require(issuer.startsWith("http://") || issuer.startsWith("https://")) { "issuer phải là URL tuyệt đối: $issuer" }
        require(!issuer.endsWith("/")) { "issuer không được có '/' cuối: $issuer" }
        require("openid" in scopes) { "scope phải có 'openid'" }
    }
}
