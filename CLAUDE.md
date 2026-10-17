# finance-mobile — hướng dẫn cho AI agent

## 1. Repo này là gì

Client Kotlin Multiplatform (Android + iOS) dùng **Compose Multiplatform UI chung**, cho app Finance.
**Kotlin 2.3.21 · AGP 8.13.2 · Compose Multiplatform 1.11.1 (Material3 1.9.0) · Gradle 8.13 · Ktor 3.5.2 · SQLDelight 2.3.2 · Koin 4.2.2.**

- Package gốc: `com.uit.finance` · compileSdk 36 · minSdk 26 · targetSdk 36
- Kiến trúc đã **chốt** ở `../ARCHITECTURE.md` (repo-level, tiếng Việt). Không tự đổi.
- Hợp đồng API: `api/openapi.yaml` là bản **copy nguyên văn** từ `finance-backend`, pin theo `api/VERSION` (hiện `2.2.0`). **Không sửa tay file này.**
- Feature hiện có: `feature/auth` (reference feature: SignedOut / Profile; login/register diễn ra trên **Keycloak** qua system browser, ADR-004) · `feature/home` (Trang chủ: thẻ ví, thu/chi tháng này, giao dịch gần đây, badge đồng bộ) · `feature/accounts` (danh sách ví, tạo/sửa ví) · `feature/transactions` (lịch sử, thêm/sửa giao dịch + phí chuyển, danh mục).
- UI theo bản thiết kế tham chiếu Purrweb "Finance Mobile iOS App" (dribbble.com/shots/21793126): Montserrat, sage `#94C1B2` / cam đào `#FFC789` / nền `#121212`. Token và component ở `core/designsystem`; icon là Lucide nhưng **chỉ** import trong `FinanceIcons.kt`.
- Dữ liệu ledger (ví, danh mục, giao dịch) + sync offline-first: `core/ledger` + `core/sync`, theo `../docs/LEDGER-PLAN.md`.
- Ngân sách theo danh mục (ADR-007 backend, `../docs/BUDGET-PLAN.md`): dữ liệu + sync ở `core/budget`; "đã chi" tính từ giao dịch qua `SpendingRepository` (core/ledger), không lưu. Màn hình (`feature/budgets`) chưa có.
- `finance-backend` là repo riêng — **không sửa từ đây**.

## 2. Commands

Mọi lệnh thường dùng đều là target trong `Makefile`. Gõ `make` để xem danh sách đầy đủ.

| Lệnh | Chạy gì |
|---|---|
| `make` | Liệt kê mọi target (mặc định = `make help`) |
| `make emulator` | Mở máy ảo Android chạy nền (mặc định AVD `Pixel_8`) |
| `make wait` | Chờ máy ảo boot xong |
| `make install` | Build code mới + cài đè APK debug lên máy ảo |
| `make open` | Mở app trên máy ảo (không build lại) |
| `make stop` | Force-stop app |
| `make log` | Logcat realtime của riêng app |
| `make crash` | Chỉ theo dõi warning + crash |
| `make apk` | Build APK debug, không cài |
| `make test` | Unit test **mọi module KMP** (chạy trên host JVM) |
| `make arch` | 24 luật Konsist |
| `make lint` | Android Lint cho `composeApp` |
| `make tunnel URL=https://…` | Trỏ build debug tới URL HTTPS của `make tunnel` bên backend (ghi `finance.publicBaseUrl` vào `local.properties`). Có URL thì APK **tắt** cleartext |
| `make tunnel-off` | Quay về loopback `10.0.2.2` / `localhost` (http) |
| `make api` | Sinh lại Kotlin client từ `api/openapi.yaml` |
| `make ios` | Compile klib iOS **không cần Xcode** |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` | Link framework iOS (**cần Xcode**) |
| `cd iosApp && xcodegen generate && open iosApp.xcodeproj` | Mở project iOS (cần `brew install xcodegen`) |

Chạy nối tiếp nhiều việc: `make emulator wait install open`, `make test arch apk`.
Máy ảo được tự dò qua `adb devices`; chỉ định tay bằng `make log DEVICE=emulator-5556`.

Không có ktlint/detekt/spotless trong repo. **Konsist là lint kiến trúc** — đó là cổng chất lượng chính.
CI (`.github/workflows/`): job `android` (ubuntu, APK + `testDebugUnitTest` + Konsist) và job `ios` (macOS, link framework + xcodegen + `xcodebuild`).

## 3. Architecture map

```
composeApp/           App(), FinanceTheme, Koin composition root (initKoin/startKoinIos),
                      MainActivity + FinanceApplication (Android), MainViewController() (iOS)
  navigation/         FinanceNavHost (session → NavHost + Scaffold), TopLevelDestination (tab bottom bar),
                      MainNavigationBar, NavControllerExtensions, placeholder/ (màn tạm cho feature chưa có module)
iosApp/               XcodeGen project.yml + vỏ SwiftUI (.xcodeproj git-ignored)
api/                  openapi.yaml + VERSION — hợp đồng pin, xem api/README.md
build-logic/          4 convention plugin: finance.kmp.library / .kmp.compose / .kmp.feature / .compose.application
gradle/libs.versions.toml   version catalog — MỌI version nằm ở đây

core/common           AppResult / AppError / FieldError, Money (Long minor units), DispatcherProvider,
                      UuidGenerator, Clock + observeToday (Flow "hôm nay", phát lại lúc 00:00), PlatformInfo. Kotlin thuần
core/presentation     MviViewModel<State, Intent, Effect>, UiText + AppError.toUiText(), MoneyFormatter (corePresentationModule),
                      DateLabels (toRelativeLabel / toLongLabel)
core/network          Ktor client (ContentNegotiation, logging sanitize, timeout, bearer + refresh rotation,
                      IdempotencyKeyPlugin, RFC 7807 → ApiException), UserApi, port TokenProvider + TokenRefresher
core/auth             OIDC client Keycloak: PKCE S256, discovery, token/refresh/logout, AuthorizationLauncher
                      (Android Custom Tabs · iOS ASWebAuthenticationSession), implement TokenRefresher
core/database         SQLDelight FinanceDatabase **mỗi user một file** `finance-<userId>.db`, mã hoá SQLCipher (khoá 256-bit
                      trong SecureStorage): outbox (+ outbox_ref), sync_cursor, sync_rejection, sync_deferred, account,
                      category, ledger_transaction, budget. Schema version 3, migration ở `sqldelight/migrations/*.sqm` (đổi bảng
                      = thêm file .sqm + cập nhật UserDatabaseMigrationTest). UserDatabaseProvider (StateFlow DB đang mở) +
                      UserDatabases (open/close/delete; chỉ xoá file khi sai khoá/mất khoá thật)
core/datastore        SecureStorage (Android Keystore AES/GCM · iOS Keychain AfterFirstUnlockThisDeviceOnly), AppSettings,
                      SessionStore, DatabaseKeyStore
core/sync             OutboxWriter (chỉ gộp vào op cuối hàng + SyncRef), SyncEngine (push/pull 1 cursor; giữ thứ tự theo bản
                      ghi và tham chiếu; batch theo số op + dung lượng, 400/413 thì chia đôi; RETRY = backoff, không bỏ op;
                      bản ghi chưa đọc được cất vào sync_deferred; stop() trước khi đổi/đóng DB), SyncRemoteDataSource,
                      OutboxRepository (unsent/stuck/retryAllNow), SyncIssueRepository, SyncScheduler
core/ledger           Model + validate ví/danh mục/giao dịch, repository (ghi entity + outbox 1 transaction),
                      số dư Flow từ SUM, SpendingRepository ("đã chi" theo danh mục gốc + tiền tệ cho ngân sách),
                      SyncChangeApplier áp thay đổi từ server. Module duy nhất đụng bảng ledger
core/budget           Model + validate ngân sách (khớp ADR-007), BudgetRepository (bản ghi + outbox + outbox_ref tới danh
                      mục trong 1 transaction), BudgetSyncChangeApplier (discard khi REJECTED budget.duplicate). Module duy
                      nhất đụng bảng budget. Payload qua BudgetPayloadCodec (core/network)
core/session          UserSession: sau /me mở DB của user + chạy sync; đăng xuất xoá DB + khoá; DB của user khác trên máy
core/designsystem     FinanceTheme (Color/Typography Montserrat/Shape/Spacing 4dp/Motion), FinanceIcons (facade Lucide) +
                      categoryIcon(key), component: AccountCard (+contourPattern), QuickActionButton, FinanceListItem,
                      IconTile, AmountText, StatTile, SectionHeader, PagerIndicator, Avatar, CircleIconButton, LabelChip,
                      EmptyState, pressScale, PrimaryButton, FinanceTextField, LoadingIndicator, ErrorBanner, ConfirmDialog,
                      form: AmountField, SegmentedControl, ChoiceTile, SelectionField, OptionSheet, DatePickerSheet,
                      ColorSwatchPicker · màn: FinanceTopBar, FinanceFab, OverflowMenu, SwipeToDeleteBox, RefreshContainer,
                      MessageSnackbarHost, SyncStatusBadge, MoneyListItem · CategoryPalette, MoneyDirection
core/ui               UI biết model ledger, dùng chung giữa feature: nhãn/icon/chiều tiền (LedgerVisuals), TransactionItemFormatter +
                      TransactionListItem, mã lỗi ledger.* → UiText (toLedgerUiText / messageFor), SyncStatusIndicator (coreUiModule)
core/testing          TestDispatcherProvider, TestClock, FakeUuidGenerator, MainDispatcherRule
core/ledger-testing   Fake repository của core/ledger + core/sync (FakeSyncKit) cho commonTest của các feature
core/budget-testing   FakeBudgetRepository cho commonTest của các feature
feature/auth          domain / data / presentation trong CÙNG một Gradle module
feature/home          Trang chủ: domain (ObserveHomeDashboardUseCase gom ví + số dư + thu/chi tháng + giao dịch gần đây từ
                      repository của core/ledger) + presentation (HomeUiMapper format, HomeScreen theo bản thiết kế)
feature/accounts      domain (use case ví: tổng quan, lưu, lưu trữ, xoá, đổi thứ tự) + presentation (list, editor)
feature/transactions  domain (lịch sử, form, lưu giao dịch + phí D3, danh mục) + presentation (history, editor, categories,
                      categoryeditor). Vuốt xoá = xoá trễ để hoàn tác (S4 xoá luôn thắng)
architecture-test     24 luật Konsist (JVM)
```

Chiều phụ thuộc: **`presentation → domain ← data`**. Feature không phụ thuộc feature. Core không phụ thuộc feature.
Mỗi Gradle module expose **đúng một** Koin module public (`coreNetworkModule`, `authFeatureModule`…); phần còn lại `internal`.

## 4. Rule bắt buộc (luôn áp dụng)

Đọc đầy đủ trong `.claude/rules/`:

| File | Nội dung |
|---|---|
| `architecture-boundaries.md` | presentation ↛ data, domain thuần, feature isolation, 24 luật Konsist |
| `mvi-and-state.md` | MviViewModel, State/Intent/Effect, Route vs Screen |
| `kmp-and-platform.md` | source set, expect/actual, Dispatchers, giới hạn iOS |
| `contract-pinning.md` | `api/openapi.yaml` pin, generated client chỉ ở `core/network` |
| `error-handling.md` | AppResult/AppError, exception không vượt ranh giới |
| `testing-policy.md` | commonTest, fake, Turbine, Konsist |

Tóm tắt không được vi phạm:
1. `presentation` không import `data`; `domain` chỉ Kotlin thuần (không Ktor/SQLDelight/Compose/Koin/Android/serialization).
2. Không import `kotlinx.coroutines.Dispatchers` ở đâu ngoài `DispatcherProvider` và `core/testing` — inject `DispatcherProvider`.
3. `GlobalScope` bị cấm tuyệt đối.
4. Class generated (`com.uit.finance.core.network.generated.*`) chỉ được import trong `core/network`.
5. `*ViewModel` phải `internal` và nằm trong `..presentation..`; `*UseCase` nằm trong `..domain..` và có đúng một `operator fun invoke`.
6. `*State` trong presentation phải là `data class` với toàn bộ property `val`.
7. Không sửa tay `api/openapi.yaml` — thay đổi API bắt đầu từ repo backend.

## 5. Dùng skill nào khi nào

| Task | Skill |
|---|---|
| Tạo feature module mới | `.claude/skills/add-feature-module/SKILL.md` |
| Sửa/thêm màn hình Compose | `.claude/skills/edit-compose-screen/SKILL.md` |
| Gọi endpoint backend từ app | `.claude/skills/add-api-call/SKILL.md` |
| Cập nhật bản pin hợp đồng API | `.claude/skills/update-api-contract/SKILL.md` |
| Debug login/refresh/401, session mất | `.claude/skills/debug-auth-session/SKILL.md` |
| Build & verify Android/iOS trước bàn giao | `.claude/skills/build-and-verify/SKILL.md` |

## 6. Ghi chú về công cụ & môi trường

- MCP `code-review-graph`: repo này **chưa có** cache graph (`list_repos_tool` = 0 registered, kiểm tra 2026-09-18). Muốn dùng phải `build_or_update_graph_tool` trước; nếu không, dùng Grep/Glob/Read là hợp lệ.
- `local.properties` (git-ignored) phải có `sdk.dir` trỏ Android SDK.
- Gradle daemon pin **Java 21 Adoptium (Temurin)** ở `gradle/gradle-daemon-jvm.properties`. Thiếu vendor thì Gradle có thể chọn JRE không có `jlink` (vd JRE của extension Java trong VS Code) → `:composeApp` fail ở bước JdkImageTransform.
- **Máy dev hiện tại không có Xcode** ⇒ mọi task liên quan link framework iOS / `xcodebuild` sẽ fail ở local; compile klib (`compileKotlinIosSimulatorArm64`) thì vẫn chạy được. Nói rõ với user thay vì tìm cách né.
- `build/`, `.gradle/`, `.kotlin/`, `bin/`, `iosApp/*.xcodeproj` là output — đừng đọc/sửa.
- Nâng version thư viện: **chỉ sửa `gradle/libs.versions.toml`**, và đọc comment cảnh báo trong đó trước (một số version mới hơn cố tình không được dùng vì xung đột AGP/Kotlin metadata).
