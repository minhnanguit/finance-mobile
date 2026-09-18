---
name: add-feature-module
description: Tạo feature module KMP mới trong finance-mobile (Gradle module, domain/data/presentation, Koin module, nav graph). Dùng khi task là "thêm feature transactions/budget", "màn hình mới thuộc luồng mới", "scaffold module".
---

# Thêm feature module mới

Mẫu để copy hình dạng: `feature/auth`. Đọc trước: `.codex/rules/architecture-boundaries.md`, `.codex/rules/mvi-and-state.md`.

## 1. Đăng ký Gradle module

`settings.gradle.kts`:

```kotlin
include(":feature:<name>")
```

`feature/<name>/build.gradle.kts` — giữ ngắn, convention plugin lo phần còn lại:

```kotlin
plugins {
    alias(libs.plugins.finance.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // CHỈ data layer dùng các module này (architecture test enforce)
            implementation(projects.core.network)
            implementation(projects.core.datastore)
            // implementation(projects.core.database)  // nếu cần cache local
        }
    }
}
```

`finance.kmp.feature` đã kèm: Compose, Koin (core/viewmodel/compose), lifecycle-viewmodel, navigation-compose, kotlinx-serialization, kotlinx-datetime, `core:common`, `core:presentation`, `core:designsystem`, và `core:testing` + Turbine cho `commonTest`.

## 2. Package layout (trong cùng một Gradle module)

```
feature/<name>/src/commonMain/kotlin/com/mosaicglobal/finance/feature/<name>/
├─ domain/       model/ · repository/ (interface) · usecase/ · validation/
├─ data/         remote/ · repository/ (Impl, internal) · mapper/
├─ presentation/ <screen>/ (Contract + ViewModel + Screen) · navigation/
└─ di/           <Name>FeatureModule.kt  ← property public DUY NHẤT của module
```

## 3. Domain

```kotlin
// domain/repository/XRepository.kt — interface, public
interface XRepository { suspend fun load(): AppResult<X> }

// domain/usecase/LoadXUseCase.kt — đúng MỘT operator fun invoke
class LoadXUseCase(private val repository: XRepository) {
    suspend operator fun invoke(): AppResult<X> = repository.load()
}
```

Domain **không** import Ktor, SQLDelight, Compose, Koin, Android, `kotlinx.serialization`. Konsist chặn.

## 4. Data

```kotlin
internal class XRepositoryImpl(private val remote: XRemoteDataSource, ...) : XRepository {
    override suspend fun load(): AppResult<X> = remote.load().map { it.toDomain() }
}
```

- Remote data source bọc `AuthApi`/`UserApi`… của `core/network`, mỗi call trong `apiCall { }`.
- Mapper DTO ↔ domain viết tay ở `data/mapper/`.
- Mọi class ở `data` là `internal`.

## 5. Presentation

Xem `.codex/rules/mvi-and-state.md`. Mỗi màn hình: `XContract.kt` + `XViewModel.kt` (internal) + `XScreen.kt` (Route + Screen).

`presentation/navigation/<Name>Navigation.kt`:

```kotlin
@Serializable data object XDestination

fun NavGraphBuilder.xNavGraph(onNavigateToY: () -> Unit, onDone: () -> Unit) {
    composable<XDestination> { XRoute(onDone = onDone) }
}
```

## 6. Koin

`di/<Name>FeatureModule.kt` — **một** property public:

```kotlin
val xFeatureModule: Module = module {
    singleOf(::XRemoteDataSource)
    singleOf(::XRepositoryImpl) { bind<XRepository>() }
    factoryOf(::LoadXUseCase)          // use case là factory
    viewModelOf(::XViewModel)
}
```

Rồi đăng ký ở `composeApp/src/commonMain/kotlin/.../di/` (đọc `KoinInit.kt` để biết danh sách module) và thêm `implementation(projects.feature.x)` vào `composeApp/build.gradle.kts`, nối `xNavGraph(...)` vào `FinanceNavHost`.

## 7. Test

`src/commonTest/`: test use case (fake repository), test repository (fake remote), test ViewModel (Turbine). Mẫu `FakeAuthRepository`.

## Checklist

- [ ] `settings.gradle.kts` có `include(":feature:<name>")`
- [ ] Build file dùng `alias(libs.plugins.finance.kmp.feature)`, không hardcode version
- [ ] Đúng **một** property public `…FeatureModule` ở package `di`
- [ ] `*ViewModel` internal ở `presentation`, `*UseCase` ở `domain` có đúng một `operator fun invoke`, `*RepositoryImpl` internal ở `data`
- [ ] Không import feature khác
- [ ] `./gradlew testDebugUnitTest :architecture-test:test :composeApp:assembleDebug` xanh
