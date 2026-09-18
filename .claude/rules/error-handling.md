# Rule — Xử lý lỗi

Nguyên tắc một câu: **exception không bao giờ vượt qua ranh giới layer.** Mọi thất bại di chuyển dưới dạng `AppResult.Failure(AppError)`.

## `AppResult`

```kotlin
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
```

Combinator có sẵn (`core/common/result/AppResult.kt`): `map`, `flatMap`, `mapError`, `fold`, `onSuccess`, `onFailure`, `getOrNull`, `errorOrNull`, `getOrElse`, `asSuccess()`, `asFailure()`. Dùng chúng thay vì `when` thủ công.

## `AppError`

| Biến thể | Khi nào |
|---|---|
| `Network(message)` | Mất kết nối, DNS, connect/read timeout |
| `Api(status, code, title, detail, fieldErrors)` | Server trả RFC 7807 non-2xx (trừ 401) |
| `Unauthorized` | 401 và refresh token không cứu được — session đã mất |
| `Validation(fieldErrors)` | Input bị chặn **trước khi** ra mạng |
| `Storage(message)` | SQLDelight / secure storage lỗi |
| `Unknown(message)` | Còn lại. Message chỉ để log, **không** hiển thị nguyên văn |

## Ai làm gì

| Layer | Trách nhiệm |
|---|---|
| `core/network` | Nơi **duy nhất** biến HTTP/transport exception thành `AppError`. `apiCall { }` bắt `ApiException`, timeout, `IOException`, `SerializationException`; `CancellationException` được **rethrow** |
| `data` (repository) | Bọc **mọi** lời gọi remote bằng `apiCall { }`. Được import `ApiException` (chỉ package `data` và `core/network` được phép) |
| `domain` (use case) | Validate input trước khi gọi repository, trả `AppResult.Failure(AppError.Validation(...))` — mẫu `LoginUseCase` + `CredentialRules` |
| `presentation` | `fold` kết quả, map `AppError` → `UiText` qua `toUiText()` hoặc extension `withError(...)` của màn hình |

## Cấm

- `try/catch` bắt `Exception` chung ở presentation hoặc domain.
- Ném exception từ use case hoặc repository ra ngoài.
- `catch (e: Exception)` mà không rethrow `CancellationException` trước.
- Hiển thị `AppError.Unknown.message` hoặc detail kỹ thuật cho người dùng.
- Log token / password / email đầy đủ. Ktor `Logging` đã sanitize header `Authorization` và `Cookie` — đừng gỡ.

## Validation

Rule validate của client phải **khớp** hợp đồng backend (mẫu `feature/auth/domain/validation/CredentialRules.kt`). Validate sớm để tiết kiệm round-trip, nhưng server vẫn là người quyết định cuối; lỗi 400/422 từ server phải map ra `fieldErrors` và hiển thị đúng field.
