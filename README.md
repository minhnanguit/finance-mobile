# finance-mobile

Kotlin Multiplatform client (Android + iOS, shared Compose Multiplatform UI) of the Mobile Financial
Management system. Companion of `finance-backend`; both follow `ARCHITECTURE.md` (Clean Architecture +
MVI per feature, offline-first with SQLDelight + outbox, contract-first API).

## Requirements

| Tool | Version |
|---|---|
| JDK | 17+ (the build uses Android Studio's bundled JBR if `JAVA_HOME` points at it) |
| Android SDK | compileSdk 36; set `sdk.dir` in `local.properties` (git-ignored) |
| Xcode | 16+ **only for iOS** – Kotlin/Native and the `iosApp` wrapper need it |
| XcodeGen | `brew install xcodegen` (iOS only) |
| Backend | `finance-backend/deploy/docker-compose.yml` on `localhost:8080` |

Gradle 9.3 wrapper, Kotlin 2.3.21, AGP 8.13.2, Compose Multiplatform 1.11.1 (Material3 1.9.0) — all versions live in
`gradle/libs.versions.toml`.

```properties
# local.properties (not committed)
sdk.dir=/Users/<you>/Library/Android/sdk
```

## Run

Every routine command is a `make` target — run `make` with no argument to list them all.

```sh
# Android (emulator reaches the host backend via http://10.0.2.2:8080 in debug builds)
make emulator wait    # boot the AVD (default Pixel_8) and wait until it is ready
make install open     # build + install the debug APK, then launch it
make log              # stream logcat for this app only

# iOS (macOS with Xcode)
cd iosApp && xcodegen generate && open iosApp.xcodeproj   # scheme "iosApp", any iOS 16+ simulator
```

The iOS build phase runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` to produce the
`ComposeApp` framework. See `iosApp/README.md`.

## Verify

```sh
make apk      # Android APK only, no install
make test     # unit tests of every KMP module (run on the host JVM)
make arch     # Konsist architecture rules
make ios      # compile the iOS klib — no Xcode needed
```

## Module map

```
composeApp/           App(), FinanceTheme + NavHost, Koin composition root (initKoin / startKoinIos),
                      MainActivity, FinanceApplication, MainViewController() for iOS
iosApp/               XcodeGen project.yml + SwiftUI shell (generated .xcodeproj is git-ignored)
api/                  Pinned OpenAPI contract (openapi.yaml + VERSION) – see api/README.md
build-logic/          Convention plugins: finance.kmp.library / .kmp.compose / .kmp.feature / .compose.application

core/common           AppResult / AppError, Money (minor units, same-currency arithmetic), DispatcherProvider,
                      UuidGenerator, Clock, PlatformInfo. Pure Kotlin + coroutines/datetime/Kermit.
core/presentation     MviViewModel<State, Intent, Effect> base, UiText + AppError -> UiText mapping.
core/network          Ktor HttpClient factory (ContentNegotiation, Kermit logging, timeouts, base URL,
                      bearer auth + refresh-token rotation, Idempotency-Key plugin, RFC 7807 -> ApiException),
                      generated OpenAPI client hidden behind AuthApi / UserApi, TokenProvider port.
core/database         SQLDelight FinanceDatabase (outbox, sync_cursor) + DatabaseDriverFactory expect/actual.
core/datastore        SecureStorage (Android Keystore AES/GCM + SharedPreferences · iOS Keychain),
                      AppSettings (multiplatform-settings), SessionStore (implements TokenProvider).
core/sync             SyncEngine, OutboxRepository, SyncCursorStore, ConflictPolicy (last-write-wins),
                      SyncScheduler (WorkManager · BGTaskScheduler). Remote side is a no-op until /sync exists.
core/designsystem     FinanceTheme (light/dark), typography, spacing, PrimaryButton, FinanceTextField,
                      LoadingIndicator, ErrorBanner.
core/testing          TestDispatcherProvider, TestClock, FakeUuidGenerator, MainDispatcherRule (JUnit4).

feature/auth          Reference feature. Packages domain / data / presentation inside one Gradle module:
                      AuthRepository + use cases, AuthRepositoryImpl (AuthApi + SessionStore + mappers),
                      Login / Register / Profile screens with MVI ViewModels, authNavGraph(...) contract.
architecture-test     Konsist rules (JVM): presentation !-> data, pure domain, feature isolation, generated
                      client confined to core/network, *UseCase in domain, *ViewModel in presentation, ...
```

Dependency direction: `presentation -> domain <- data`; features never depend on other features; core never
depends on features. Every module exposes exactly one public Koin module (`coreNetworkModule`,
`authFeatureModule`, ...); implementations are `internal`.

## Contract pinning

`api/openapi.yaml` is a verbatim copy of the backend contract at the version in `api/VERSION`.
`:core:network:openApiGenerate` (openapi-generator 7.14, `kotlin` / `multiplatform` / kotlinx-serialization)
turns it into an `internal` client at build time; only `core/network/.../api/internal/GeneratedApiAdapters.kt`
may import it. Upgrading the contract is a deliberate PR — procedure in `api/README.md`.

## Auth flow at runtime

1. `LoginViewModel` -> `LoginUseCase` (validation) -> `AuthRepositoryImpl` -> `AuthApi.login` (+ device info,
   `Idempotency-Key`).
2. Tokens are stored through `SessionStore` (Keystore / Keychain); `TokenCache.invalidate()` makes Ktor reload them.
3. Every protected call carries `Authorization: Bearer`. On 401 the Ktor `Auth` plugin calls
   `POST /api/v1/auth/refresh`, persists the rotated pair and retries once. If the refresh is rejected the
   session is cleared, `observeSession()` emits `null` and the NavHost returns to Login.
4. Non-2xx responses are parsed as RFC 7807 into `ApiException` and converted to `AppError` by `apiCall {}`;
   no exception crosses a layer boundary.

## Known limitations of this core

- iOS: all modules compile to Kotlin/Native klibs for `iosArm64` / `iosSimulatorArm64` (`make ios`)
  without Xcode, but linking the `ComposeApp` framework and running the app require Xcode (see CI `ios` job).
- `core/sync` remote data source is a no-op: the v1 contract has no `/sync` endpoints yet.
- Strings are English literals resolved through `UiText`; a Compose resources catalog can replace `UiText.Key` later.
