package com.uit.finance.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uit.finance.app.navigation.placeholder.AccountsDestination
import com.uit.finance.app.navigation.placeholder.AddTransactionDestination
import com.uit.finance.app.navigation.placeholder.BillsDestination
import com.uit.finance.app.navigation.placeholder.ReportsDestination
import com.uit.finance.app.navigation.placeholder.placeholderNavGraph
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.feature.auth.domain.usecase.ObserveSessionUseCase
import com.uit.finance.feature.auth.presentation.navigation.SignedOutDestination
import com.uit.finance.feature.auth.presentation.navigation.authNavGraph
import com.uit.finance.feature.home.presentation.navigation.HomeDestination
import com.uit.finance.feature.home.presentation.navigation.HomeTarget
import com.uit.finance.feature.home.presentation.navigation.homeNavGraph
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

    Scaffold(
        // safeDrawing = system bars + tai thỏ + bàn phím, nên từng màn không phải tự xử lý inset.
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (isAuthenticated && currentTab != null) {
                MainNavigationBar(current = currentTab, onSelect = navController::navigateToTopLevel)
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(contentPadding),
        ) {
            authNavGraph(
                onAuthenticated = { navController.navigateClearingBackStack(HomeDestination) },
                onLoggedOut = { navController.navigateClearingBackStack(SignedOutDestination) },
            )
            homeNavGraph(onNavigate = navController::open)
            placeholderNavGraph(onBack = { navController.navigateUp() })
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
    HomeTarget.Reports -> navigateSingleTop(ReportsDestination)
    HomeTarget.Bills -> navigateSingleTop(BillsDestination)
    HomeTarget.AddTransaction -> navigateSingleTop(AddTransactionDestination)
}
