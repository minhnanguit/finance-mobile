# Rule — MVI & state

Mọi màn hình theo cùng một khuôn: `MviViewModel<State, Intent, Effect>` (`core/presentation`).

## Ba file mỗi màn hình

```
presentation/<screen>/
├─ <Screen>Contract.kt     State (data class) + Intent (sealed interface) + Effect (sealed interface)
├─ <Screen>ViewModel.kt     internal class …ViewModel(deps) : MviViewModel<…>(…State())
└─ <Screen>Screen.kt        <Screen>Route(...) + <Screen>Screen(state, onIntent, modifier)
```

## Contract

```kotlin
data class LoginState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && !isSubmitting   // derived: computed property, không lưu
}

sealed interface LoginIntent {
    data class EmailChanged(val value: String) : LoginIntent
    data object Submit : LoginIntent
}

sealed interface LoginEffect {
    data object NavigateToHome : LoginEffect
}
```

- **State**: `data class`, mọi property `val`, có default → Konsist kiểm tra. Là nguồn sự thật duy nhất của màn hình.
- Text hiển thị dùng `UiText` (`Dynamic` hoặc `Key(key, fallback)`), **không** `String` thô cho thông báo lỗi — state phải serialisable & test được.
- **Intent**: mọi thứ người dùng/hệ thống gây ra. **Effect**: one-shot (navigate, snackbar), không phải state.

## ViewModel

```kotlin
internal class LoginViewModel(private val loginUseCase: LoginUseCase) :
    MviViewModel<LoginState, LoginIntent, LoginEffect>(LoginState()) {

    override fun onIntent(intent: LoginIntent) = when (intent) { ... }   // when exhaustive, không else
}
```

- Chỉ `setState { copy(...) }` và `sendEffect(...)`; không expose `MutableStateFlow`.
- Gọi suspend qua `viewModelScope.launch`. **Không** `GlobalScope`, không `Dispatchers.IO` trực tiếp — inject `DispatcherProvider` nếu cần đổi dispatcher.
- ViewModel gọi **use case**, không gọi repository trực tiếp, không gọi Ktor/SQLDelight.
- Kết quả xử lý bằng `fold(onSuccess = …, onFailure = …)` trên `AppResult`.
- Map `AppError` → state bằng extension `internal fun XState.withError(error: AppError)` đặt cạnh ViewModel; case đặc thù (401 = sai mật khẩu, 429 = thử lại sau) xử lý ở đó, còn lại rơi về `error.toUiText()`.
- `effects` là Channel buffered (capacity 64, DROP_OLDEST) — đừng đổi thành `SharedFlow` conflated.

## Route vs Screen

```kotlin
@Composable
internal fun LoginRoute(onNavigateToRegister: () -> Unit, onLoggedIn: () -> Unit,
                        viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.effects.collect { effect -> when (effect) { ... } } }
    LoginScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun LoginScreen(state: LoginState, onIntent: (LoginIntent) -> Unit, modifier: Modifier = Modifier)
```

- **Route** biết Koin + navigation + effect. **Screen** là hàm thuần state-in / intent-out → preview và test được, không đụng ViewModel.
- Chỉ `collectAsStateWithLifecycle`, không `collectAsState`.
- Effect collect trong `LaunchedEffect(viewModel)`, không collect trong composable body.
