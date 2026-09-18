---
name: add-api-call
description: Gọi một endpoint backend từ finance-mobile (remote data source, repository, mapper, error). Dùng khi task là "gọi API X", "lấy dữ liệu từ server", "gửi form lên backend", "thêm network call".
---

# Thêm lời gọi API

Đọc trước: `.claude/rules/contract-pinning.md`, `.claude/rules/error-handling.md`.

## 0. Endpoint đã có trong hợp đồng chưa?

```bash
grep -n "^  /api" api/openapi.yaml
cat api/VERSION
```

- **Có** → tiếp bước 1.
- **Chưa** → dừng. Không tự thêm path vào `api/openapi.yaml`. Báo user: backend phải bổ sung endpoint và release contract mới; sau đó dùng skill `update-api-contract`.
- Lưu ý: contract v1 **chưa có** `/api/v1/sync/*` — `core/sync` remote hiện là no-op.

## 1. Expose trong `core/network` (nếu chưa có)

`core/network/src/commonMain/kotlin/.../api/`:

- Interface thủ công `AuthApi` / `UserApi`… — đây là thứ feature nhìn thấy.
- DTO ở `api/model/` (`AuthDtos.kt`, `UserDtos.kt`).
- Implement bằng adapter ở `api/internal/GeneratedApiAdapters.kt` — **nơi duy nhất** được import `…core.network.generated.*`.
- Đăng ký trong `core/network/di/CoreNetworkModule.kt`.

Những thứ **tự động**, đừng làm tay:

| Thứ | Ai lo |
|---|---|
| `Authorization: Bearer` | Ktor `Auth` plugin (gửi proactively cho path không bắt đầu bằng `/api/v1/auth/`) |
| Refresh khi 401 + retry | `Auth` plugin → `POST /api/v1/auth/refresh`, rotate, lưu qua `TokenProvider` |
| `Idempotency-Key` | `IdempotencyKeyPlugin` |
| Base URL, timeout, JSON | `HttpClientFactory` + `NetworkConfig` |
| Non-2xx → `ApiException` | `HttpResponseValidator` + `ProblemDetails` |

## 2. Remote data source (trong feature, package `data/remote`)

```kotlin
internal class XRemoteDataSource(private val api: XApi) {
    suspend fun load(id: String): AppResult<XDto> = apiCall { api.load(id) }
}
```

`apiCall { }` là **bắt buộc** — nó là chỗ duy nhất biến exception thành `AppError`.

## 3. Mapper (`data/mapper`)

Viết tay, hai chiều khi cần: `internal fun XDto.toDomain(): X`, `internal fun X.toDto(): XDto`.
DTO **không** được rò lên `domain` hay `presentation`.

## 4. Repository (`data/repository`)

```kotlin
internal class XRepositoryImpl(private val remote: XRemoteDataSource) : XRepository {
    override suspend fun load(id: String): AppResult<X> = remote.load(id).map { it.toDomain() }
}
```

Kết hợp nhiều nguồn (remote + local) thì dùng `flatMap` / `fold`, không `try/catch`.

## 5. Use case & UI

Use case ở `domain/usecase` validate input rồi gọi repository; ViewModel `fold` kết quả. Xem `.claude/rules/mvi-and-state.md`.

## 6. Ghi vào lúc offline

App là offline-first: mutation khi mất mạng ghi vào `outbox` (SQLDelight, `core/database/Outbox.sq`) qua `OutboxRepository` rồi `SyncEngine` đẩy lên sau.
`core/sync` remote **hiện là no-op** vì contract v1 chưa có `/sync`. Trước khi dựa vào nó, đọc `core/sync/engine/DefaultSyncEngine.kt` — **verify before use**.

## 7. Test

- Mock HTTP bằng `ktor-client-mock` (mẫu `core/network/src/commonTest/.../TestSupport.kt`).
- Test repository bằng fake remote, assert cả nhánh `Success` lẫn từng `AppError`.

## Checklist

- [ ] Endpoint có trong `api/openapi.yaml` đang pin
- [ ] Không import class `generated.*` ngoài `core/network`
- [ ] Mọi call bọc `apiCall { }`
- [ ] Không gắn tay header `Authorization` / `Idempotency-Key`
- [ ] DTO không rò ra ngoài `data`
- [ ] Test phủ `Success`, `Network`, `Unauthorized`, `Api(4xx)`
