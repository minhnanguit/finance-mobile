package com.uit.finance.feature.auth.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.uit.finance.feature.auth.presentation.localdata.LocalDataGateRoute
import com.uit.finance.feature.auth.presentation.profile.ProfileRoute
import com.uit.finance.feature.auth.presentation.signedout.SignedOutRoute
import kotlinx.serialization.Serializable

/** Destination type-safe của feature auth. `composeApp` gắn chúng vào NavHost. */
@Serializable data object SignedOutDestination
@Serializable data object ProfileDestination

/** Chặn vào app khi sổ trên máy chưa mở được (lần đầu đăng nhập mà mất mạng, S6). */
@Serializable data object LocalDataRequiredDestination

/** Hợp đồng navigation của feature: app quyết định đi *đâu*, feature quyết định *khi nào*. */
fun NavGraphBuilder.authNavGraph(
    onAuthenticated: () -> Unit,
    onLoggedOut: () -> Unit,
    onLocalDataReady: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    composable<SignedOutDestination> {
        SignedOutRoute(onSignedIn = onAuthenticated)
    }
    composable<ProfileDestination> {
        ProfileRoute(onLoggedOut = onLoggedOut)
    }
    composable<LocalDataRequiredDestination> {
        LocalDataGateRoute(onReady = onLocalDataReady, onOpenProfile = onOpenProfile)
    }
}
