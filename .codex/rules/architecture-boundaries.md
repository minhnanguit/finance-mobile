# Rule — Ranh giới kiến trúc (KMP + Clean Architecture)

Luôn áp dụng. Vi phạm là **fail** ở `./gradlew :architecture-test:test` (Konsist), không phải góp ý review.

## Chiều phụ thuộc

```
presentation ──► domain ◄── data
```

- `presentation` **không** biết `data` tồn tại.
- `domain` không biết cả hai, và không biết framework nào.
- `feature/*` không import `feature/*` khác.
- `core/*` không import `feature/*`.

Một feature = **một Gradle module** chứa cả ba package `domain/`, `data/`, `presentation/` (+ `di/`). Không tách thành 3 Gradle module.

## 15 luật Konsist (file `architecture-test/src/test/kotlin/…`)

### `LayerDependencyRulesTest`

| Luật | Ý nghĩa |
|---|---|
| presentation ↛ data | File trong package `presentation` không import package `data` cùng root |
| domain thuần Kotlin | Cấm import `io.ktor.`, `app.cash.sqldelight.`, `androidx.`, `android.`, `org.jetbrains.compose.`, `org.koin.`, `platform.`, `kotlinx.serialization.`, `com.russhwolf.settings.` và mọi package `data`/`presentation` |
| feature ↛ feature | Feature này không import feature khác |
| core ↛ feature | Core không import `…finance.feature.` |
| generated client bị nhốt | `…core.network.generated.*` chỉ được import trong `…core.network` |
| ApiException bị nhốt | Ngoài `core/network`, chỉ package `data` được import `ApiException` |
| Dispatchers bị nhốt | `kotlinx.coroutines.Dispatchers` / `IO` chỉ trong `DispatcherProvider.kt` và `core/testing` |
| Cấm `GlobalScope` | Tuyệt đối, mọi module |

### `NamingAndPlacementRulesTest`

| Luật | Ý nghĩa |
|---|---|
| `*UseCase` ở `..domain..` | Và phải có **đúng một** `operator fun invoke` |
| `*ViewModel` ở `..presentation..` | Và phải `internal` (trừ `MviViewModel` base) |
| `*RepositoryImpl` | `internal` + ở `..data..` |
| `*State` trong presentation | `data class`, mọi property là `val` |
| Koin module | Property tên kết thúc `Module` phải ở package `..di..` |

## Visibility

| Thứ | Visibility |
|---|---|
| Koin module của Gradle module (`authFeatureModule`, `coreNetworkModule`) | `public` — **đúng một cái mỗi module** |
| Use case, domain model, repository interface (domain) | `public` trong feature module |
| `*RepositoryImpl`, remote data source, mapper, `*ViewModel`, `*Screen`, `*Route` | `internal` |
| Class generated từ OpenAPI | `internal` (đã ép bằng `nonPublicApi=true`) |

## Wiring giữa module

- Feature expose điểm vào navigation là **một hàm** `NavGraphBuilder.<feature>NavGraph(...)` nhận callback điều hướng; `composeApp` quyết định đi đâu, feature quyết định khi nào (mẫu `authNavGraph`).
- Destination là `@Serializable data object/class` trong `presentation/navigation` của feature.
- `composeApp` là nơi duy nhất biết tất cả feature. Composition root là `initKoin()` trong `composeApp/src/commonMain/.../di/KoinInit.kt` — thêm Koin module mới ở đúng đây (Android gọi từ `FinanceApplication`, iOS từ `startKoinIos()`).

## Khi cần phá luật

Không tự sửa `architecture-test`. Nêu vấn đề cho user kèm đề xuất; thay đổi luật kiến trúc là quyết định của họ.
