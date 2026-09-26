package com.mosaicglobal.finance.feature.auth.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mosaicglobal.finance.feature.auth.presentation.profile.ProfileRoute
import com.mosaicglobal.finance.feature.auth.presentation.signedout.SignedOutRoute
import kotlinx.serialization.Serializable

/** Destination type-safe của feature auth. `composeApp` gắn chúng vào NavHost. */
@Serializable data object SignedOutDestination
@Serializable data object ProfileDestination

/** Hợp đồng navigation của feature: app quyết định đi *đâu*, feature quyết định *khi nào*. */
fun NavGraphBuilder.authNavGraph(
    onAuthenticated: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    composable<SignedOutDestination> {
        SignedOutRoute(onSignedIn = onAuthenticated)
    }
    composable<ProfileDestination> {
        ProfileRoute(onLoggedOut = onLoggedOut)
    }
}
