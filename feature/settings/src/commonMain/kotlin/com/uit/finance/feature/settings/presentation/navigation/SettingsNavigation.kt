package com.uit.finance.feature.settings.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.uit.finance.feature.settings.presentation.deleteaccount.DeleteAccountRoute
import com.uit.finance.feature.settings.presentation.settings.SettingsRoute
import kotlinx.serialization.Serializable

/** Tab "Cá nhân". */
@Serializable data object SettingsDestination

@Serializable data object DeleteAccountDestination

/**
 * Hợp đồng navigation của feature: app quyết định đi *đâu*, feature quyết định *khi nào*.
 *
 * @param onSignedOut đăng xuất hoặc xoá tài khoản xong: phiên đã kết thúc, về màn đăng nhập
 */
fun NavGraphBuilder.settingsNavGraph(
    onBack: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
    onSignedOut: () -> Unit,
) {
    composable<SettingsDestination> {
        SettingsRoute(onOpenDeleteAccount = onOpenDeleteAccount, onLoggedOut = onSignedOut)
    }
    composable<DeleteAccountDestination> {
        DeleteAccountRoute(onBack = onBack, onDeleted = onSignedOut)
    }
}
