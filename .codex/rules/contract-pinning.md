# Rule — Pin hợp đồng API

## Nguyên tắc

`api/openapi.yaml` là **bản copy nguyên văn** hợp đồng của `finance-backend`. `api/VERSION` giữ semver đang pin (hiện `2.0.0` = `info.version` của spec).

**Không bao giờ sửa tay `api/openapi.yaml` trong repo này.** Mọi thay đổi API bắt đầu ở repo backend, rồi mới re-pin ở đây.

## Client sinh ra bị nhốt

- `:core:network:openApiGenerate` sinh Kotlin client (openapi-generator 7.14, generator `kotlin`, library `multiplatform`, kotlinx-serialization) vào `core/network/build/generated/openapi`, package `com.uit.finance.core.network.generated.*`.
- `nonPublicApi=true` ⇒ mọi type generated là `internal`.
- **Chỉ** `core/network/src/commonMain/kotlin/.../api/internal/GeneratedApiAdapters.kt` được import package generated. Konsist chặn phần còn lại của repo.
- Feature nhìn thấy: `UserApi` (interface viết tay trong `core/network/api/`) + DTO trong `core/network/api/model/`. Không gì khác.
- Contract 2.0.0 không còn `/auth/*`: login, register, refresh token đều đi thẳng tới Keycloak qua `core/auth`, **không** qua generated client.

## Đừng đụng các cấu hình sau nếu không có lý do rõ ràng

Trong `core/network/build.gradle.kts` đã có comment giải thích; đọc trước khi đổi:

- `typeMappings` map `date-time` → `kotlin.time.Instant` (kotlinx-datetime 0.7+ chuyển Instant vào stdlib).
- **Không** set `serializationLibrary` — `library=multiplatform` đã ngụ ý kotlinx-serialization; set thêm sẽ sinh `@Serializable` hai lần.
- `enumPropertyNaming=UPPERCASE`, `sourceFolder=src/commonMain/kotlin`.

## Quy ước hợp đồng (phải tuân theo khi gọi API)

| Chủ đề | Quy ước |
|---|---|
| Idempotency | Mọi POST/PUT/PATCH cần header `Idempotency-Key` (UUID). `IdempotencyKeyPlugin` tự gắn — đừng gắn tay |
| Lỗi | RFC 7807 → `ProblemDetails` → `ApiException` → `AppError` |
| Tiền | Integer minor units + ISO-4217, không float |
| Paging | Cursor-based |
| Version | Breaking change ⇒ backend lên major + `/api/v2`; `/v1` còn sống ít nhất 2 release |

## Khi cần API mới chưa có trong spec

Không tự thêm path vào `api/openapi.yaml`. Báo user: cần backend bổ sung endpoint và release contract mới, sau đó dùng skill `update-api-contract` để re-pin.

Lưu ý hiện trạng: `core/sync` có remote data source **no-op** vì contract v1 chưa có `/api/v1/sync/*`. Đừng giả định endpoint sync tồn tại.
