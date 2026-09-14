package com.mosaicglobal.finance.feature.auth.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mosaicglobal.finance.feature.auth.presentation.login.LoginRoute
import com.mosaicglobal.finance.feature.auth.presentation.profile.ProfileRoute
import com.mosaicglobal.finance.feature.auth.presentation.register.RegisterRoute
import kotlinx.serialization.Serializable

/** Type-safe destinations owned by the auth feature. `composeApp` wires them into the NavHost. */
@Serializable data object LoginDestination
@Serializable data object RegisterDestination
@Serializable data object ProfileDestination

/**
 * Navigation contract of the feature: the app decides *where* to go, the feature decides *when*.
 */
fun NavGraphBuilder.authNavGraph(
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onAuthenticated: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    composable<LoginDestination> {
        LoginRoute(onNavigateToRegister = onNavigateToRegister, onLoggedIn = onAuthenticated)
    }
    composable<RegisterDestination> {
        RegisterRoute(onNavigateToLogin = onNavigateToLogin, onRegistered = onAuthenticated)
    }
    composable<ProfileDestination> {
        ProfileRoute(onLoggedOut = onLoggedOut)
    }
}
