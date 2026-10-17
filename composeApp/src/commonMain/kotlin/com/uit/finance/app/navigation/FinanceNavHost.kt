package com.uit.finance.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uit.finance.app.devtools.DevMenu
import com.uit.finance.app.di.isDebugBuild
import com.uit.finance.app.navigation.placeholder.BillsDestination
import com.uit.finance.app.navigation.placeholder.ReportsDestination
import com.uit.finance.app.navigation.placeholder.placeholderNavGraph
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.feature.accounts.presentation.navigation.AccountEditorDestination
import com.uit.finance.feature.accounts.presentation.navigation.AccountsDestination
import com.uit.finance.feature.accounts.presentation.navigation.accountsNavGraph
import com.uit.finance.feature.auth.domain.model.LocalDataStatus
import com.uit.finance.feature.budgets.presentation.navigation.BudgetEditorDestination
import com.uit.finance.feature.budgets.presentation.navigation.budgetsNavGraph
import com.uit.finance.feature.auth.domain.usecase.ObserveLocalDataStatusUseCase
import com.uit.finance.feature.auth.domain.usecase.ObserveSessionUseCase
import com.uit.finance.feature.auth.presentation.navigation.LocalDataRequiredDestination
import com.uit.finance.feature.auth.presentation.navigation.ProfileDestination
import com.uit.finance.feature.auth.presentation.navigation.SignedOutDestination
import com.uit.finance.feature.auth.presentation.navigation.authNavGraph
import com.uit.finance.feature.home.presentation.navigation.HomeDestination
import com.uit.finance.feature.home.presentation.navigation.HomeTarget
import com.uit.finance.feature.home.presentation.navigation.homeNavGraph
import com.uit.finance.feature.transactions.presentation.navigation.CategoriesDestination
import com.uit.finance.feature.transactions.presentation.navigation.CategoryEditorDestination
import com.uit.finance.feature.transactions.presentation.navigation.TransactionEditorDestination
import com.uit.finance.feature.transactions.presentation.navigation.transactionsNavGraph
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

private sealed interface SessionUiState {
    data object Loading : SessionUiState
    data class Ready(val isAuthenticated: Boolean) : SessionUiState
}

@Composable
internal fun FinanceNavHost() {
    val observeSession = koinInject<ObserveSessionUseCase>()
    val sessionState by remember(observeSession) {
        observeSession().map<Any?, SessionUiState> { SessionUiState.Ready(isAuthenticated = it != null) }
    }.collectAsStateWithLifecycle(initialValue = SessionUiState.Loading)

    when (val current = sessionState) {
        SessionUiState.Loading -> LoadingIndicator()
        is SessionUiState.Ready -> MainNavHost(isAuthenticated = current.isAuthenticated)
    }
}

@Composable
private fun MainNavHost(isAuthenticated: Boolean) {
    val navController = rememberNavController()
    // Chỉ quyết định một lần cho mỗi NavHost; các lần chuyển sau đều là lệnh navigate tường minh.
    val startDestination: Any = remember { if (isAuthenticated) HomeDestination else SignedOutDestination }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentTab = currentDestination.currentTopLevel()
    val observeLocalData = koinInject<ObserveLocalDataStatusUseCase>()
    val localData by remember(observeLocalData) { observeLocalData() }
        .collectAsStateWithLifecycle(initialValue = LocalDataStatus.Preparing)

    Scaffold(
        // safeDrawing = system bars + tai thỏ + bàn phím, nên từng màn không phải tự xử lý inset. consumeWindowInsets bên
        // dưới đánh dấu đã dùng, để màn nào lỡ gọi imePadding()/safeDrawingPadding() cũng không bị đệm bàn phím lần hai.
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (isAuthenticated && currentTab != null) {
                MainNavigationBar(current = currentTab, onSelect = navController::navigateToTopLevel)
            }
        },
    ) { contentPadding ->
        Box(Modifier.padding(contentPadding).consumeWindowInsets(contentPadding)) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
                authNavGraph(
                    onAuthenticated = { navController.navigateClearingBackStack(HomeDestination) },
                    onLoggedOut = { navController.navigateClearingBackStack(SignedOutDestination) },
                    onLocalDataReady = { navController.navigateClearingBackStack(HomeDestination) },
                    onOpenProfile = { navController.navigateSingleTop(ProfileDestination) },
                )
                homeNavGraph(onNavigate = navController::open)
                accountsNavGraph(
                    onBack = { navController.navigateUp() },
                    onOpenEditor = { id -> navController.navigateSingleTop(AccountEditorDestination(id)) },
                )
                transactionsNavGraph(
                    onBack = { navController.navigateUp() },
                    onOpenTransaction = { id -> navController.navigateSingleTop(TransactionEditorDestination(id)) },
                    onOpenCategories = { kind -> navController.navigateSingleTop(CategoriesDestination(kind.name)) },
                    onOpenCategory = { id, kind -> navController.navigateSingleTop(CategoryEditorDestination(id, kind.name)) },
                    onCreateAccount = { navController.navigateSingleTop(AccountEditorDestination()) },
                )
                budgetsNavGraph(
                    onBack = { navController.navigateUp() },
                    onOpenEditor = { id -> navController.navigateSingleTop(BudgetEditorDestination(id)) },
                )
                placeholderNavGraph(onBack = { navController.navigateUp() })
            }
            // Menu DEV (dữ liệu mẫu…) chỉ có ở build debug và khi đã có sổ của user để ghi vào.
            if (isDebugBuild && isAuthenticated) {
                // Góc trái dưới: góc phải là chỗ của nút + (thêm giao dịch, thêm danh mục).
                DevMenu(Modifier.align(Alignment.BottomStart).padding(FinanceTheme.spacing.md))
            }
        }
    }

    // Lần đầu đăng nhập mà mất mạng (S6) hoặc không mở được sổ: chặn ở màn giải thích + thử lại. Hồ sơ vẫn mở được
    // từ màn đó để đăng xuất.
    LaunchedEffect(isAuthenticated, localData, currentDestination) {
        val blocked = localData == LocalDataStatus.NeedsNetwork || localData == LocalDataStatus.Unavailable
        val onGate = currentDestination?.hasRoute<LocalDataRequiredDestination>() == true ||
            currentDestination?.hasRoute<ProfileDestination>() == true
        if (isAuthenticated && blocked && currentDestination != null && !onGate) {
            navController.navigateClearingBackStack(LocalDataRequiredDestination)
        }
    }

    // Refresh token bị Keycloak reject ở background → session bị clear → rời mọi màn hình cần đăng nhập.
    LaunchedEffect(isAuthenticated, currentDestination) {
        val onProtectedScreen = currentDestination != null && !currentDestination.hasRoute<SignedOutDestination>()
        if (!isAuthenticated && onProtectedScreen) {
            navController.navigateClearingBackStack(SignedOutDestination)
        }
    }
}

/** Màn đích nào là tab thì chuyển tab (không đẩy thêm bản sao lên back stack), còn lại mở như màn con. */
private fun NavHostController.open(target: HomeTarget) = when (target) {
    HomeTarget.Transactions -> navigateToTopLevel(TopLevelDestination.Transactions)
    HomeTarget.Budgets -> navigateToTopLevel(TopLevelDestination.Budgets)
    HomeTarget.Accounts -> navigateSingleTop(AccountsDestination)
    HomeTarget.CreateAccount -> navigateSingleTop(AccountEditorDestination())
    HomeTarget.Reports -> navigateSingleTop(ReportsDestination)
    HomeTarget.Bills -> navigateSingleTop(BillsDestination)
    HomeTarget.AddTransaction -> navigateSingleTop(TransactionEditorDestination())
    is HomeTarget.EditTransaction -> navigateSingleTop(TransactionEditorDestination(target.transactionId))
}
