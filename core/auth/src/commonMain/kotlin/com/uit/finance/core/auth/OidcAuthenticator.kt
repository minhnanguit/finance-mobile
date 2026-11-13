package com.uit.finance.core.auth

import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.network.auth.AuthTokens

/** API mà data layer của feature dùng để login / logout với Keycloak. */
interface OidcAuthenticator {

    /**
     * Mở system browser tới trang của Keycloak (Authorization Code + PKCE), rồi đổi code lấy token.
     * User tự đóng browser thì trả [AuthorizationOutcome.Cancelled], không phải lỗi.
     */
    suspend fun authorize(prompt: AuthorizationPrompt = AuthorizationPrompt.SignIn): AppResult<AuthorizationOutcome>

    /**
     * Bắt user nhập lại mật khẩu/passkey ngay lúc này (`prompt=login`), cho thao tác không hoàn tác được như
     * xoá tài khoản (ADR-008 A2). Phiên SSO trong browser không đủ để qua bước này.
     *
     * Token mới chỉ được trả về khi thuộc **đúng** tài khoản của [currentAccessToken] (so claim `sub`). User đăng
     * nhập nhầm sang tài khoản khác ở bước này thì phiên vừa mở đó bị huỷ ngay và trả
     * [ReauthenticationOutcome.DifferentAccount]: không bao giờ xoá nhầm tài khoản kia.
     *
     * Cần access token là JWT có claim `sub` (Keycloak luôn phát JWT; realm có client scope `basic`). Token không đọc
     * được `sub` thì luôn coi là [ReauthenticationOutcome.DifferentAccount] — an toàn, nhưng sẽ không xoá được tài
     * khoản cho tới khi cấu hình realm đúng.
     */
    suspend fun reauthenticate(currentAccessToken: String): AppResult<ReauthenticationOutcome>

    /**
     * Mở form đổi mật khẩu của Keycloak (Application Initiated Action `kc_action=UPDATE_PASSWORD`). Keycloak tự bắt
     * nhập mật khẩu hiện tại khi lần đăng nhập gần nhất đã quá 5 phút (cấu hình "Maximum age of authentication" của
     * required action), và có ô "Đăng xuất khỏi thiết bị khác".
     *
     * Ô đó có thể huỷ luôn phiên app đang giữ (khi trình duyệt không còn cookie của phiên ấy), nên khi đổi thành công
     * cho **đúng** tài khoản của [currentAccessToken] thì trả token mới để thay phiên hiện tại. Đổi cho tài khoản khác
     * (đăng nhập nhầm ở trình duyệt) thì phiên vừa mở bị huỷ và trả [PasswordChangeOutcome.DifferentAccount].
     */
    suspend fun changePassword(currentAccessToken: String): AppResult<PasswordChangeOutcome>

    /** Huỷ session trên Keycloak bằng refresh token — không mở browser. */
    suspend fun endSession(refreshToken: String): AppResult<Unit>
}

enum class AuthorizationPrompt {
    SignIn,

    /** Mở thẳng form đăng ký (`prompt=create`). */
    SignUp,

    /** Bắt nhập lại thông tin đăng nhập dù browser còn phiên SSO (`prompt=login`, `max_age=0`). */
    Reauthenticate,

    /** Form đổi mật khẩu của Keycloak (`kc_action=UPDATE_PASSWORD`); kết quả nằm ở `kc_action_status`. */
    ChangePassword,
}

sealed interface AuthorizationOutcome {
    data class Authorized(val tokens: AuthTokens) : AuthorizationOutcome
    data object Cancelled : AuthorizationOutcome
}

sealed interface ReauthenticationOutcome {
    /** Đúng tài khoản đang đăng nhập, vừa nhập lại mật khẩu. Token mới mang `auth_time` mới. */
    data class Confirmed(val tokens: AuthTokens) : ReauthenticationOutcome

    /** User tự đóng trang đăng nhập. */
    data object Cancelled : ReauthenticationOutcome

    /** User đăng nhập bằng tài khoản khác; phiên của tài khoản đó đã bị huỷ, không dùng token nào. */
    data object DifferentAccount : ReauthenticationOutcome
}

sealed interface PasswordChangeOutcome {
    /** Đã đổi mật khẩu của tài khoản đang đăng nhập. [tokens] thuộc phiên vẫn còn sống sau khi đổi. */
    data class Changed(val tokens: AuthTokens) : PasswordChangeOutcome

    /** User bấm huỷ trên form hoặc đóng trình duyệt: mật khẩu giữ nguyên. */
    data object Cancelled : PasswordChangeOutcome

    /** Trình duyệt đăng nhập bằng tài khoản khác nên mật khẩu đổi là của tài khoản đó; phiên của nó đã bị huỷ. */
    data object DifferentAccount : PasswordChangeOutcome
}
