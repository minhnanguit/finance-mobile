# Rule — Testing policy

| Lệnh | Chạy gì | Cần gì |
|---|---|---|
| `make test` | Unit test mọi module KMP (target Android/JVM, chạy trên host) | JDK + Android SDK |
| `make arch` | 17 luật Konsist (đọc **source**, không phải bytecode) | JDK |
| `make apk` | Build APK — smoke test biên dịch | Android SDK |

CI chạy cả ba (job `android`) + build iOS (job `ios`, cần macOS + Xcode).

## Viết test ở đâu

| Đối tượng | Nơi | Công cụ |
|---|---|---|
| Domain model, use case | `<module>/src/commonTest/…/domain/` | `kotlin.test` + fake repository (mẫu `FakeAuthRepository` trong `feature/auth/src/commonTest/…/testing/`) |
| Repository (data) | `commonTest/…/data/repository/` | Fake remote + fake `SessionStore` (mẫu `AuthRepositoryImplTest`) |
| ViewModel | `commonTest/…/presentation/` | **Turbine** cho `state`/`effects` (mẫu `SignedOutViewModelTest`), `TestDispatcherProvider` |
| Ktor client, plugin | `core/network/src/commonTest/` | `ktor-client-mock` (mẫu `HttpClientFactoryTest`, `IdempotencyKeyPluginTest`, `TestSupport.kt`) |
| OIDC / Keycloak | `core/auth/src/commonTest/` | `FakeKeycloak` (MockEngine) + `FakeAuthorizationLauncher`. PKCE kiểm bằng vector RFC 7636 |
| Thứ chỉ chạy trên JVM/Android | `src/androidUnitTest/` | Mẫu `SqlDelightOutboxRepositoryTest` |
| Luật kiến trúc | `architecture-test/` | Konsist — chỉ sửa khi user đồng ý |

## Quy tắc

- Test ở `commonTest` trước; chỉ xuống `androidUnitTest` khi thật sự cần JVM-only.
- **Fake > mock.** Repo dùng fake class viết tay, không có mocking framework trong version catalog.
- Thời gian: `TestClock`. Id: `FakeUuidGenerator`. Dispatcher: `TestDispatcherProvider`. Đừng dùng giờ/UUID thật.
- Flow/StateFlow: Turbine (`turbine` trong catalog), không `delay()` + assert.
- ViewModel test: `MainDispatcherRule` (JUnit4, `core/testing` androidMain) cho test chạy trên Android target.
- **Không có UI test Compose trong repo hiện tại.** `ARCHITECTURE.md` có nhắc UI test Compose như mục tiêu — nếu task yêu cầu, phải thêm dependency + cấu hình mới: **verify before use**, hỏi user trước.

## Định nghĩa "xong"

Trước khi báo hoàn thành:

```bash
make test
make arch
make apk     # nếu đụng UI / DI / build config
```

Đụng code iOS-specific mà máy không có Xcode ⇒ tối thiểu `make ios`, và nói rõ phần link framework **chưa verify được**.

Báo cáo trung thực: lệnh nào chưa chạy thì nói là chưa chạy.
