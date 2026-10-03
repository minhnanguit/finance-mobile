package com.uit.finance.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.FinanceIcon
import com.uit.finance.core.designsystem.component.FinanceIconType
import com.uit.finance.feature.auth.domain.usecase.ObserveSessionUseCase
import com.uit.finance.feature.auth.presentation.navigation.ProfileDestination
import com.uit.finance.feature.auth.presentation.navigation.SignedOutDestination
import com.uit.finance.feature.auth.presentation.navigation.authNavGraph
import com.uit.finance.feature.home.presentation.home.HomeTarget
import com.uit.finance.feature.home.presentation.navigation.AddTransactionDestination
import com.uit.finance.feature.home.presentation.navigation.BillsDestination
import com.uit.finance.feature.home.presentation.navigation.BudgetsDestination
import com.uit.finance.feature.home.presentation.navigation.HomeDestination
import com.uit.finance.feature.home.presentation.navigation.ReportsDestination
import com.uit.finance.feature.home.presentation.navigation.TransactionsDestination
import com.uit.finance.feature.home.presentation.navigation.WalletsDestination
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
        is SessionUiState.Ready -> {
            val navController = rememberNavController()
            // Chỉ quyết định một lần cho mỗi NavHost; các lần chuyển sau đều là lệnh navigate tường minh.
            val startDestination: Any = remember { if (current.isAuthenticated) HomeDestination else SignedOutDestination }
            val backStackEntry by navController.currentBackStackEntryAsState()
            val showMainNavigation = current.isAuthenticated && backStackEntry != null &&
                backStackEntry?.destination?.hasRoute<SignedOutDestination>() != true

            Scaffold(
                bottomBar = {
                    if (showMainNavigation) {
                        MainNavigationBar(
                            currentDestination = backStackEntry?.destination,
                            onSelect = navController::navigateTopLevel,
                        )
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
                    homeNavGraph(
                        onNavigate = { target ->
                            val destination: Any = when (target) {
                                HomeTarget.AddTransaction -> AddTransactionDestination
                                HomeTarget.Transactions -> TransactionsDestination
                                HomeTarget.Wallets -> WalletsDestination
                                HomeTarget.Budgets -> BudgetsDestination
                                HomeTarget.Reports -> ReportsDestination
                                HomeTarget.Bills -> BillsDestination
                            }
                            navController.navigate(destination)
                        },
                        onBack = {
                            if (!navController.navigateUp()) navController.navigateTopLevel(HomeDestination)
                        },
                    )
                }
            }

            // Refresh token bị Keycloak reject ở background → session bị clear → rời màn hình cần đăng nhập.
            LaunchedEffect(current.isAuthenticated, backStackEntry) {
                val onProtectedScreen = backStackEntry?.destination?.hasRoute<SignedOutDestination>() == false
                if (!current.isAuthenticated && onProtectedScreen) {
                    navController.navigateClearingBackStack(SignedOutDestination)
                }
            }
        }
    }
}

@Composable
private fun MainNavigationBar(
    currentDestination: NavDestination?,
    onSelect: (Any) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = currentDestination?.hasRoute<TransactionsDestination>() != true &&
                currentDestination?.hasRoute<BudgetsDestination>() != true &&
                currentDestination?.hasRoute<ProfileDestination>() != true,
            onClick = { onSelect(HomeDestination) },
            icon = { FinanceIcon(FinanceIconType.Home) },
            label = { Text("Trang chủ") },
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute<TransactionsDestination>() == true,
            onClick = { onSelect(TransactionsDestination) },
            icon = { FinanceIcon(FinanceIconType.Transactions) },
            label = { Text("Giao dịch") },
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute<BudgetsDestination>() == true,
            onClick = { onSelect(BudgetsDestination) },
            icon = { FinanceIcon(FinanceIconType.Budget) },
            label = { Text("Ngân sách") },
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute<ProfileDestination>() == true,
            onClick = { onSelect(ProfileDestination) },
            icon = { FinanceIcon(FinanceIconType.Profile) },
            label = { Text("Cá nhân") },
        )
    }
}

private fun NavHostController.navigateTopLevel(destination: Any) {
    navigate(destination) {
        popUpTo<HomeDestination> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateClearingBackStack(destination: Any) {
    navigate(destination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
