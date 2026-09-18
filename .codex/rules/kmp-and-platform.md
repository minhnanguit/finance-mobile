# Rule — KMP, source set & nền tảng

## Source set

| Thư mục | Chứa gì |
|---|---|
| `src/commonMain` | **Mặc định**. Mọi logic chung. Viết ở đây trước |
| `src/androidMain` | Chỉ khi cần API Android (Keystore, WorkManager, Context) |
| `src/iosMain` | Chỉ khi cần API Apple (Keychain, BGTaskScheduler) |
| `src/commonTest` | Test chung, chạy cho mọi target |
| `src/androidUnitTest` | Test cần JVM/Android-only (mẫu `SqlDelightOutboxRepositoryTest`) |

Quy tắc: **thứ gì viết được ở `commonMain` thì không được đẩy xuống platform source set.**

## expect / actual

Dùng khi và chỉ khi hành vi khác nhau theo nền tảng. Mẫu có sẵn:

| expect | actual Android | actual iOS |
|---|---|---|
| `DatabaseDriverFactory` | `AndroidSqliteDriver` | `NativeSqliteDriver` |
| `SecureStorage` (interface + Koin platform module) | Keystore AES-256/GCM + SharedPreferences | Keychain |
| `PlatformInfo` | `.android.kt` | `.ios.kt` |
| `platformEngine()` | OkHttp | Darwin |
| `SyncScheduler` | WorkManager | BGTaskScheduler |
| `defaultApiBaseUrl()`, `isDebugBuild` (composeApp) | `10.0.2.2:8080` khi debug | loopback simulator |

Đặt tên file: `X.kt` (expect) / `X.android.kt` / `X.ios.kt`.
DI theo nền tảng: `di/PlatformXModule.android.kt` / `.ios.kt` — expect `val platformXModule: Module`.

Target set: `androidTarget()` (jvmTarget 17) + `iosArm64()` + `iosSimulatorArm64()`, khai trong `build-logic/.../KotlinMultiplatform.kt` với `applyDefaultHierarchyTemplate()`. **Không có JVM desktop, không có wasm/js, không có `iosX64` (Intel simulator).** Đừng thêm target mới nếu không được yêu cầu.

Compiler flag đang bật: `-Xexpect-actual-classes` (expect/actual class vẫn Beta) và `-Xconsistent-data-class-copy-visibility`.

## Coroutines

- **Cấm** import `kotlinx.coroutines.Dispatchers` (và `Dispatchers.IO`) ngoài `DispatcherProvider.kt` + `core/testing` — Konsist chặn.
- **Cấm** `GlobalScope`, mọi nơi.
- Cần đổi dispatcher → inject `DispatcherProvider` (`main`, `io`, `default`, `unconfined`) và `withContext(dispatchers.io) { }`. Impl thật là `DefaultDispatcherProvider`.
- Trong ViewModel: `viewModelScope`. Trong test: `TestDispatcherProvider` + `MainDispatcherRule` (Android JUnit4).
- `CancellationException` phải luôn được rethrow, không được nuốt trong `catch (e: Exception)` — xem `apiCall` và `HttpClientFactory.rotate` làm mẫu.

## Build & version

- **Mọi version nằm ở `gradle/libs.versions.toml`.** Không hardcode version trong `build.gradle.kts`.
- File build mỗi module chỉ nên 5–25 dòng nhờ convention plugin:

| Plugin | Dùng cho |
|---|---|
| `finance.kmp.library` | core module thuần (common, network, database, datastore, sync, presentation, testing) |
| `finance.kmp.compose` | module có UI Compose (designsystem) |
| `finance.kmp.feature` | feature module (Compose + Koin + lifecycle + navigation + serialization đã kèm sẵn) |
| `finance.compose.application` | `composeApp` |

Thêm dependency chung cho mọi feature → sửa convention plugin, đừng copy vào từng module.
- `TYPESAFE_PROJECT_ACCESSORS` đang bật: dùng `projects.core.network`, không `project(":core:network")` (trừ trong build-logic).

## iOS

- Compile klib không cần Xcode: `./gradlew compileKotlinIosSimulatorArm64`.
- Link framework + chạy app **cần Xcode 16+** và `xcodegen`. `iosApp/*.xcodeproj` được sinh ra, đã gitignore — đừng commit.
- Máy dev hiện tại không có Xcode ⇒ báo rõ cho user khi task cần nó, đừng workaround.
