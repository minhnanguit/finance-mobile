package com.uit.finance.feature.auth.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.uit.finance.feature.auth.presentation.localdata.LocalDataGateRoute
import com.uit.finance.feature.auth.presentation.signedout.SignedOutRoute
import kotlinx.serialization.Serializable

/** Destination type-safe của feature auth. `composeApp` gắn chúng vào NavHost. */
@Serializable data object SignedOutDestination

/** Chặn vào app khi sổ trên máy chưa mở được (lần đầu đăng nhập mà mất mạng, S6). */
@Serializable data object LocalDataRequiredDestination

/**
 * Hợp đồng navigation của feature: app quyết định đi *đâu*, feature quyết định *khi nào*.
 *
 * @param onOpenSettings màn chặn S6 muốn đăng xuất: mở màn Cài đặt (đăng xuất nằm ở đó, ADR-008 C1)
 */
fun NavGraphBuilder.authNavGraph(
    onAuthenticated: () -> Unit,
    onLocalDataReady: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    composable<SignedOutDestination> {
        SignedOutRoute(onSignedIn = onAuthenticated)
    }
    composable<LocalDataRequiredDestination> {
        LocalDataGateRoute(onReady = onLocalDataReady, onOpenSettings = onOpenSettings)
    }
}
