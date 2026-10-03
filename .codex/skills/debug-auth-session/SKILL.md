---
name: debug-auth-session
description: Debug luồng đăng nhập Keycloak / refresh token / mất session trong finance-mobile (OIDC + PKCE qua Custom Tabs / ASWebAuthenticationSession, Ktor Auth, SessionStore, NavHost quay về SignedOut). Dùng khi gặp 401, app tự đăng xuất, browser không quay về app, token không lưu.
---

# Debug auth & session (Keycloak, ADR-004)

Code liên quan: `core/auth/` (OIDC client, PKCE, launcher theo nền tảng), `core/network/client/HttpClientFactory.kt`,
`core/network/auth/TokenProvider.kt`, `core/datastore/session/`, `feature/auth/`, `composeApp/navigation/FinanceNavHost.kt`.

## Luồng runtime

```
SignedOutViewModel → SignInUseCase → AuthRepositoryImpl → OidcAuthenticator.authorize(prompt)
  → OidcClient.discovery()          (cache in-memory; issuer trong document PHẢI == OidcConfig.issuer)
  → PKCE S256 + state
  → AuthorizationLauncher           Android: AuthorizationActivity → Custom Tab
                                    iOS:     ASWebAuthenticationSession (ephemeral)
  ← redirect com.mosaicglobal.finance://oauth/callback?code&state[&iss]
                                    Android: RedirectReceiverActivity → AuthorizationActivity.onNewIntent
  → kiểm redirect URI, state, iss   → sai bất kỳ cái nào thì dừng, KHÔNG đổi code
  → OidcClient.exchangeCode(code, code_verifier)
  → SessionStore.save(tokens)       (Android Keystore AES/GCM · iOS Keychain)
  → TokenCache.invalidate()         ← BẮT BUỘC, để Ktor nạp lại token
  → observeSession() emit Session   → FinanceNavHost đổi màn

Request tới backend: Authorization: Bearer (gửi sẵn, CHỈ tới host của NetworkConfig.baseUrl)
401 → Ktor Auth.refreshTokens → TokenRefresher (core/auth) → token endpoint của Keycloak
      Refreshed   → lưu cặp mới (refresh token đã rotate) → retry 1 lần
      Rejected    → tokenProvider.clear() → observeSession() emit null → NavHost về SignedOut
      Unavailable → GIỮ session (offline-first), request này fail
Logout → OidcClient.endSession(refresh_token) (không mở browser) → luôn clear local
```

## Triệu chứng → chỗ nghi ngờ

| Triệu chứng | Nghi ngờ |
|---|---|
| Banner "No connection" ngay khi bấm Sign in, browser không mở | Android chặn `http://`. APK phải có `usesCleartextTraffic=true` ở debug — kiểm bằng `aapt2 dump xmltree --file AndroidManifest.xml <apk>`. Cờ này đặt qua `manifestPlaceholders` trong `composeApp/build.gradle.kts`; **đừng** chuyển về `src/androidDebug/AndroidManifest.xml`, file đó không được merge trong setup KMP này |
| Keycloak báo `Invalid parameter: redirect_uri` ngay sau khi đổi package / redirect URI | `realm-finance.json` chỉ được import khi realm **chưa tồn tại**. Sửa JSON rồi thì phải đồng bộ Keycloak đang chạy: `make reset && make up` (mất dữ liệu) hoặc cập nhật client qua Admin API |
| Form đăng ký không có ô mật khẩu | Đúng hành vi Keycloak 26.7 khi realm bật `verifyEmail`: đăng ký chỉ hỏi email + tên → mail xác thực (Mailpit) → đặt mật khẩu → quay lại app đăng nhập |
| Bấm Sign in không có gì xảy ra, lỗi chung chung | Discovery fail. Log Kermit `issuer không khớp` ⇒ `defaultOidcIssuer()` ≠ `KC_HOSTNAME` của Keycloak. Android debug phải là `http://10.0.2.2:8081/realms/finance` |
| Browser mở nhưng Keycloak báo `Invalid parameter: redirect_uri` | `OIDC_REDIRECT_URI` lệch `redirectUris` trong `finance-backend/deploy/keycloak/realm-finance.json` |
| Login xong browser không quay về app (Android) | Intent-filter của `RedirectReceiverActivity` trong `composeApp/.../AndroidManifest.xml` lệch scheme/host/path của redirect URI |
| Quay về app nhưng hiện "Sign-in did not complete" | Code hết hạn / đã dùng (`invalid_grant`), hoặc `state` không khớp. Thử lại; nếu lặp lại thì xem log |
| Login xong request tới backend vẫn 401 | Quên `tokenCache.invalidate()`; hoặc token thiếu `aud=finance-api` (audience mapper trong realm) |
| App tự đăng xuất | Refresh bị reject (`invalid_grant`, mọi 4xx). Log: `Refresh token bị IdP reject`. Session Keycloak hết hạn (30 ngày) hoặc đã logout ở nơi khác |
| Mất mạng thì bị đá ra ngoài | Không được xảy ra: lỗi mạng / 5xx là `Unavailable`, session giữ nguyên. Nếu bị, xem `OidcTokenRefresher.toOutcome` |
| 401 nhưng không thấy refresh chạy | `HttpResponseValidator` được cài trước plugin user nên 401 tới tay `Auth` trước — đổi thứ tự cài plugin là hỏng. Đừng đổi |
| Bị đá về SignedOut khi đang ở Profile | Đúng thiết kế: `LaunchedEffect` trong `FinanceNavHost` theo dõi `isAuthenticated`; session bị clear ở background |
| Token mất sau khi kill app | `SecureStorage` theo nền tảng: `AndroidKeystoreSecureStorage` / `KeychainSecureStorage` |
| iOS simulator login luôn fail | Chưa bật tunnel: `KC_HOSTNAME=10.0.2.2` chỉ đúng cho Android emulator. Bật `make tunnel` (backend) + `make tunnel URL=...` (mobile) |
| Mọi thứ đột nhiên 401 / "Sign-in did not complete" sau khi bật lại tunnel | Quick Tunnel đã đổi URL ⇒ issuer đổi. Chạy lại `make tunnel` bên backend, `make run`, rồi `make tunnel URL=<url mới> && make install` |

## Công cụ

- Log HTTP của **backend client**: `NetworkConfig(logHttp = true)` — debug build đã bật. Header `Authorization`/`Cookie` **đã sanitize**; đừng gỡ.
- OIDC client (`core/auth`) không log HTTP (body chứa token), nhưng `oidcCall` log **mọi exception** kèm class + message: `make log`, lọc `OIDC call thất bại`.
- Test để đọc/chạy: `core/auth/src/commonTest/` (PKCE theo vector RFC 7636, state/iss/redirect, refresh), `core/network/.../HttpClientFactoryTest.kt`, `feature/auth/.../AuthRepositoryImplTest.kt`, `SignedOutViewModelTest.kt`.
- Backend + Keycloak thật: `cd ../finance-backend && make up run`, Admin Console `make kc`, mail verify `make mail`.
- Log app trên máy ảo: `make log` hoặc `make crash`.

`finance.publicBaseUrl` (local.properties, `make tunnel URL=...`) có giá trị thì **cả API lẫn issuer** dùng URL HTTPS đó
(`<url>` và `<url>/realms/finance`) cho mọi nền tảng — iOS simulator cũng login được. Bảng dưới là khi KHÔNG có tunnel.

| Cấu hình | Debug Android | Debug iOS | Release |
|---|---|---|---|
| `defaultApiBaseUrl()` | `http://10.0.2.2:8080` | `http://localhost:8080` | placeholder |
| `defaultOidcIssuer()` | `http://10.0.2.2:8081/realms/finance` | `http://localhost:8081/realms/finance` ⚠️ | placeholder |

Host release là placeholder — **verify before use**.

## Ràng buộc bảo mật khi sửa

- Không WebView (Konsist chặn `android.webkit.` / `platform.WebKit.`). Chỉ `core/auth` được import `androidx.browser.` / `platform.AuthenticationServices.`.
- Không bỏ kiểm `state`, `iss`, redirect URI, issuer của discovery — mỗi cái chặn một kiểu tấn công.
- Không log token, refresh token, email đầy đủ.
- Access token TTL 5 phút do **Keycloak** quản, không "sửa" ở client.
- `logout` là best-effort: gọi Keycloak, lỗi thế nào cũng phải `sessionStore.clear()` + `tokenCache.invalidate()`.
