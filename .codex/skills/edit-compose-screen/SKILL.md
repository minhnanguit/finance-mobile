---
name: edit-compose-screen
description: Thêm hoặc sửa màn hình / component Compose Multiplatform trong finance-mobile (MVI, design system, navigation). Dùng khi task là "sửa UI", "thêm màn hình", "đổi layout", "thêm nút", "thêm trường nhập".
---

# Sửa / thêm màn hình Compose

Đọc trước: `.codex/rules/mvi-and-state.md`. Mẫu đầy đủ: `feature/auth/presentation/login/`.

## Trước khi gõ code

1. Xác định màn hình thuộc feature nào → sửa trong `feature/<name>/presentation/<screen>/`.
2. Component tái sử dụng được nhiều feature → đặt ở `core/designsystem/component/`, **không** copy sang feature.

## Dùng design system, không hardcode

| Cần gì | Dùng |
|---|---|
| Khoảng cách | `FinanceTheme.spacing.{xxs,xs,sm,md,lg,xl,xxl}` (4dp grid) — **không** `16.dp` literal |
| Màu | `MaterialTheme.colorScheme.*` (`FinanceTheme` đã set sáng/tối) |
| Typography | `MaterialTheme.typography.*` |
| Nút chính / phụ | `PrimaryButton`, `SecondaryTextButton` |
| Ô nhập | `FinanceTextField` |
| Loading / lỗi | `LoadingIndicator`, `ErrorBanner` |

Thiếu component → thêm vào `core/designsystem/component/` theo phong cách các file có sẵn, rồi mới dùng.

## Khuôn Route / Screen

```kotlin
@Composable
internal fun XRoute(onDone: () -> Unit, viewModel: XViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect -> when (effect) { XEffect.Done -> onDone() } }
    }
    XScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun XScreen(state: XState, onIntent: (XIntent) -> Unit, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = spacing.lg)) { ... }
}
```

Quy tắc:

- `Screen` **không** nhận ViewModel, không gọi Koin → thuần state-in/intent-out.
- `modifier: Modifier = Modifier` là tham số cuối trong danh sách có default.
- `safeDrawingPadding()` cho màn hình full-screen (insets đúng trên cả Android lẫn iOS).
- Không `collectAsState` — chỉ `collectAsStateWithLifecycle`.
- Không logic nghiệp vụ trong composable: mọi quyết định đi qua `onIntent`.
- Text hiện tại là literal tiếng Anh resolve qua `UiText`; chuỗi trong **state** phải là `UiText`, chuỗi tĩnh trong layout thì để literal như các màn hình hiện có.

## Thêm sự kiện mới

1. Thêm biến thể vào `XIntent` (sealed interface).
2. Xử lý trong `onIntent` — `when` exhaustive, **không** dùng `else`.
3. Cần điều hướng/snackbar → thêm biến thể `XEffect`, `sendEffect(...)`, và xử lý trong `LaunchedEffect` của Route.

## Thêm màn hình vào navigation

1. `@Serializable data object XDestination` trong `presentation/navigation/` của feature.
2. Thêm `composable<XDestination> { XRoute(...) }` vào hàm `…NavGraph(...)` của feature.
3. `composeApp/.../FinanceNavHost.kt` truyền callback điều hướng vào. **Feature không tự quyết định đi đâu** — chỉ gọi callback.

## Verify

```bash
make apk
make test arch
```

Xem thật trên máy: `make install` (Android). iOS cần Xcode — máy dev hiện tại không có, nói rõ với user.

## Checklist

- [ ] State là `data class` toàn `val`; không giữ state cục bộ bằng `remember { mutableStateOf }` cho dữ liệu nghiệp vụ
- [ ] ViewModel `internal`, Route/Screen `internal`
- [ ] Dùng token spacing/typography/color, không literal dp/màu
- [ ] `when` trên Intent/Effect exhaustive
- [ ] Không import `data` từ `presentation`
