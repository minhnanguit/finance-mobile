---
name: build-and-verify
description: Build và verify finance-mobile trên Android/iOS trước khi bàn giao (Gradle task, Konsist, giới hạn môi trường). Dùng khi cần "build app", "chạy test", "kiểm tra trước PR", hoặc khi build lỗi toolchain/SDK/Xcode.
---

# Build & verify

## Điều kiện môi trường

| Thứ | Yêu cầu | Trạng thái máy dev hiện tại |
|---|---|---|
| JDK | 17+ (README); CI dùng temurin 21 | JBR 25 làm `JAVA_HOME` — nếu lỗi toolchain thì **verify before use** |
| Android SDK | compileSdk 36, `sdk.dir` trong `local.properties` (git-ignored) | Cần kiểm tra file tồn tại |
| Xcode | 16+ **chỉ cho iOS** | **Không có** — mọi task link framework / `xcodebuild` sẽ fail |
| XcodeGen | `brew install xcodegen` (chỉ iOS) | Cùng điều kiện trên |
| Backend | `finance-backend/deploy/docker-compose.yml` ở `localhost:8080` | Cần mở Docker Desktop trước |

Thiếu Xcode thì **nói rõ với user** phần iOS chưa verify được, đừng tìm cách vòng qua.

## Verify theo phạm vi thay đổi

| Bạn đã sửa | Chạy tối thiểu |
|---|---|
| Domain / use case / repository | `./gradlew testDebugUnitTest` |
| Bất cứ thứ gì về cấu trúc, đặt tên, import | `+ ./gradlew :architecture-test:test` |
| UI, DI, navigation, build config | `+ ./gradlew :composeApp:assembleDebug` |
| Code iOS-specific (`iosMain`) | `+ ./gradlew compileKotlinIosSimulatorArm64` (không cần Xcode) |
| `api/openapi.yaml` | `+ ./gradlew :core:network:openApiGenerate :core:network:assemble` |
| Trước PR | Cả bốn lệnh đầu |

`./gradlew testDebugUnitTest` chạy unit test của **mọi** module KMP trên host JVM (target Android/debug), không cần emulator.

## Chạy app

```bash
# Android
./gradlew :composeApp:installDebug          # hoặc mở project trong Android Studio, run composeApp

# iOS (cần Xcode)
cd iosApp && xcodegen generate && open iosApp.xcodeproj   # scheme "iosApp", simulator iOS 16+
```

Build phase của Xcode gọi `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Chi tiết: `iosApp/README.md`.

## Lỗi hay gặp

| Lỗi | Xử lý |
|---|---|
| `SDK location not found` | Tạo `local.properties` với `sdk.dir=/Users/<you>/Library/Android/sdk`. **Không commit** |
| Task iOS link fail | Thiếu Xcode. Báo user; `compileKotlinIosSimulatorArm64` vẫn chạy được để kiểm tra biên dịch |
| Konsist fail | Đọc tên test để biết luật nào vỡ (`LayerDependencyRulesTest` / `NamingAndPlacementRulesTest`), sửa **code** chứ không sửa luật |
| Class generated không tìm thấy | `./gradlew :core:network:openApiGenerate` rồi build lại |
| Xung đột version sau khi nâng thư viện | Đọc comment trong `gradle/libs.versions.toml` — vài version mới hơn **cố ý** bị chặn (Compose 1.12 cần AGP ≥ 9.1; Coil 3.5+ cần Kotlin 2.4) |

## Báo cáo

Report test: `**/build/reports/tests/**`. APK: `composeApp/build/outputs/apk/debug/*.apk`.

Nói thật về những gì đã chạy: lệnh nào chưa chạy (thiếu SDK, thiếu Docker, thiếu Xcode) thì ghi rõ là **chưa chạy** kèm lý do, không suy đoán kết quả.

## Dọn dẹp

- `build/`, `.gradle/`, `.kotlin/`, `bin/`, `iosApp/*.xcodeproj` là output, đã gitignore — đừng commit, đừng sửa.
- `./gradlew clean` hiếm khi cần; ưu tiên task cụ thể của module.
