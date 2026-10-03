---
name: build-and-verify
description: Build và verify finance-mobile trên Android/iOS trước khi bàn giao (Gradle task, Konsist, giới hạn môi trường). Dùng khi cần "build app", "chạy test", "kiểm tra trước PR", hoặc khi build lỗi toolchain/SDK/Xcode.
---

# Build & verify

## Điều kiện môi trường

| Thứ | Yêu cầu | Trạng thái máy dev hiện tại |
|---|---|---|
| JDK | 21 LTS; daemon pin Java 21, CI dùng Temurin 21 | Kiểm tra `JAVA_HOME` và `./gradlew --version`; cần JDK 21 đã cài, không dùng JBR 25 để chạy Gradle 8.13 |
| Android SDK | compileSdk 36, `sdk.dir` trong `local.properties` (git-ignored) | Cần kiểm tra file tồn tại |
| Xcode | 16+ **chỉ cho iOS** | **Không có** — mọi task link framework / `xcodebuild` sẽ fail |
| XcodeGen | `brew install xcodegen` (chỉ iOS) | Cùng điều kiện trên |
| Backend | `finance-backend/deploy/docker-compose.yml` ở `localhost:8080` | Cần mở Docker Desktop trước |

Thiếu Xcode thì **nói rõ với user** phần iOS chưa verify được, đừng tìm cách vòng qua.

## Verify theo phạm vi thay đổi

| Bạn đã sửa | Chạy tối thiểu |
|---|---|
| Domain / use case / repository | `make test` |
| Bất cứ thứ gì về cấu trúc, đặt tên, import | `+ make arch` |
| UI, DI, navigation, build config | `+ make apk` |
| Code iOS-specific (`iosMain`) | `+ make ios` (không cần Xcode) |
| `api/openapi.yaml` | `+ make api` |
| Trước PR | Cả bốn lệnh đầu |

`make test` chạy unit test của **mọi** module KMP trên host JVM (target Android/debug), không cần emulator.

## Chạy app

```bash
# Android
make emulator wait    # mở máy ảo và chờ boot (bỏ qua nếu máy ảo đã chạy)
make install open     # build + cài APK debug rồi mở app
make log              # logcat realtime của riêng app

# iOS (cần Xcode)
cd iosApp && xcodegen generate && open iosApp.xcodeproj   # scheme "iosApp", simulator iOS 16+
```

Build phase của Xcode gọi `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Chi tiết: `iosApp/README.md`.

## Lỗi hay gặp

| Lỗi | Xử lý |
|---|---|
| `SDK location not found` | Tạo `local.properties` với `sdk.dir=/Users/<you>/Library/Android/sdk`. **Không commit** |
| Task iOS link fail | Thiếu Xcode. Báo user; `make ios` vẫn chạy được để kiểm tra biên dịch |
| Konsist fail | Đọc tên test để biết luật nào vỡ (`LayerDependencyRulesTest` / `NamingAndPlacementRulesTest`), sửa **code** chứ không sửa luật |
| Class generated không tìm thấy | `make api` rồi build lại |
| Xung đột version sau khi nâng thư viện | Đọc comment trong `gradle/libs.versions.toml` — vài version mới hơn **cố ý** bị chặn (Compose 1.12 cần AGP ≥ 9.1; Coil 3.5+ cần Kotlin 2.4) |

## Báo cáo

Report test: `**/build/reports/tests/**`. APK: `composeApp/build/outputs/apk/debug/*.apk`.

Nói thật về những gì đã chạy: lệnh nào chưa chạy (thiếu SDK, thiếu Docker, thiếu Xcode) thì ghi rõ là **chưa chạy** kèm lý do, không suy đoán kết quả.

## Dọn dẹp

- `build/`, `.gradle/`, `.kotlin/`, `bin/`, `iosApp/*.xcodeproj` là output, đã gitignore — đừng commit, đừng sửa.
- `make clean` hiếm khi cần; ưu tiên task cụ thể của module.
