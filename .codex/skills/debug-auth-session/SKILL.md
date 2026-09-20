---
name: debug-auth-session
description: Debug luồng đăng nhập / refresh token / mất session trong finance-mobile (Ktor Auth, SessionStore, Keystore-Keychain, NavHost quay về Login). Dùng khi gặp 401, app tự đăng xuất, token không lưu, login không chuyển màn.
---

# Debug auth & session

Code liên quan: `core/network/client/HttpClientFactory.kt`, `core/network/auth/TokenProvider.kt`, `core/datastore/session/`, `feature/auth/`, `composeApp/navigation/FinanceNavHost.kt`.

## Luồng runtime

```
LoginViewModel → LoginUseCase (validate) → AuthRepositoryImpl
  → AuthRemoteDataSource.login(email, password, device)  [+ Idempotency-Key tự động]
  → SessionStore.save(tokens)        (Android Keystore AES/GCM · iOS Keychain)
  → TokenCache.invalidate()          ← BẮT BUỘC, để Ktor nạp lại token
  → observeSession() emit Session    → FinanceNavHost đổi màn

Mọi request protected: Authorization: Bearer (gửi sẵn, không đợi challenge)
401 → Ktor Auth.refreshTokens → POST /api/v1/auth/refresh → lưu cặp mới → retry 1 lần
Refresh hỏng → tokenProvider.clear() → observeSession() emit null → NavHost về Login
```

## Triệu chứng → chỗ nghi ngờ

| Triệu chứng | Nghi ngờ |
|---|---|
| Login thành công nhưng request sau vẫn 401 | Quên `tokenCache.invalidate()` sau khi lưu token — `BearerAuthProvider` vẫn giữ token cũ trong bộ nhớ |
| App tự đăng xuất ngay sau khi mở | Refresh rotation fail → `clear()`. Xem log Kermit `"Refresh token rotation failed; clearing session"`. Thường do backend đã restart với khoá RSA ephemeral mới |
| Refresh lặp vô hạn | `sendWithoutRequest` phải loại trừ path bắt đầu `/api/v1/auth/`; request refresh phải có `markAsRefreshTokenRequest()` |
| 401 nhưng không thấy refresh chạy | `HttpResponseValidator` được cài trước plugin user nên 401 đến tay `Auth` trước — nếu đổi thứ tự cài plugin sẽ hỏng. Đừng đổi |
| Login xong không chuyển màn | `LoginEffect.NavigateToHome` không được collect, hoặc `authNavGraph` callback chưa nối trong `FinanceNavHost` |
| Ở màn Profile bị đá về Login bất ngờ | Đúng thiết kế: `LaunchedEffect` trong `FinanceNavHost` theo dõi `isAuthenticated`; session bị clear ở background |
| Token mất sau khi kill app | `SecureStorage` platform: Android `AndroidKeystoreSecureStorage`, iOS `KeychainSecureStorage`. Kiểm tra actual của nền tảng đang chạy |
| Sai mật khẩu nhưng hiện lỗi chung chung | `LoginState.withError`: 401 → `auth.invalid_credentials`, 429 → `auth.rate_limited` |

## Công cụ

- Bật log HTTP: `NetworkConfig(logHttp = true)` — ở debug build đã bật qua `isDebugBuild`. Level `HEADERS`, header `Authorization`/`Cookie` **đã sanitize**; đừng gỡ sanitize để debug, log payload tạm thời rồi xoá.
- Test có sẵn để đọc/chạy: `core/network/src/commonTest/.../HttpClientFactoryTest.kt` (rotation), `IdempotencyKeyPluginTest.kt`, `feature/auth/src/commonTest/.../AuthRepositoryImplTest.kt`, `LoginViewModelTest.kt`.
- Backend thật:

```bash
cd ../finance-backend && make up run
```

- Log app đang chạy trên máy ảo: `make log` (chỉ log của app) hoặc `make crash` (chỉ warning + crash).

Base URL do `defaultApiBaseUrl()` quyết định (expect ở `composeApp/src/commonMain/.../di/AppModule.kt`):

| Nền tảng | Debug | Release |
|---|---|---|
| Android (`AppModule.android.kt`) | `http://10.0.2.2:8080` (loopback của emulator) | `https://api.finance.example.com` |
| iOS (`KoinIos.kt`) | `http://localhost:8080` | `https://api.finance.example.com` |

Host release là placeholder trong repo — **verify before use** trước khi dựa vào nó.

## Ràng buộc bảo mật khi sửa

- Không log token, refresh token, password, email đầy đủ.
- Refresh token là **một lần dùng**: backend rotate và revoke token cũ; dùng lại token đã rotate ⇒ backend revoke **toàn bộ session của device**. Đừng cache/thử lại refresh token cũ.
- Access token TTL 15 phút là thiết kế backend (ADR-004), không "sửa" ở client.
- `logout` là best-effort: gọi server, dù lỗi mạng vẫn phải `sessionStore.clear()` + `tokenCache.invalidate()` — giữ nguyên hành vi này.
