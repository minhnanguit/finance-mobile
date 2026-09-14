package com.mosaicglobal.finance.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import com.mosaicglobal.finance.core.designsystem.component.LoadingIndicator
import com.mosaicglobal.finance.feature.auth.domain.usecase.ObserveSessionUseCase
import com.mosaicglobal.finance.feature.auth.presentation.navigation.LoginDestination
import com.mosaicglobal.finance.feature.auth.presentation.navigation.ProfileDestination
import com.mosaicglobal.finance.feature.auth.presentation.navigation.RegisterDestination
import com.mosaicglobal.finance.feature.auth.presentation.navigation.authNavGraph
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
            // Decided once per NavHost; later transitions are explicit navigation calls.
            val startDestination: Any = remember { if (current.isAuthenticated) ProfileDestination else LoginDestination }

            NavHost(navController = navController, startDestination = startDestination) {
                authNavGraph(
                    onNavigateToRegister = { navController.navigate(RegisterDestination) },
                    onNavigateToLogin = { navController.popBackStack() },
                    onAuthenticated = { navController.navigateClearingBackStack(ProfileDestination) },
                    onLoggedOut = { navController.navigateClearingBackStack(LoginDestination) },
                )
            }

            // Token refresh failed in the background -> session cleared -> leave protected screens.
            val backStackEntry by navController.currentBackStackEntryAsState()
            LaunchedEffect(current.isAuthenticated, backStackEntry) {
                val onProtectedScreen = backStackEntry?.destination?.hasRoute<ProfileDestination>() == true
                if (!current.isAuthenticated && onProtectedScreen) {
                    navController.navigateClearingBackStack(LoginDestination)
                }
            }
        }
    }
}

private fun NavHostController.navigateClearingBackStack(destination: Any) {
    navigate(destination) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
