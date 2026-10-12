package com.uit.finance.feature.accounts.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.uit.finance.feature.accounts.presentation.editor.AccountEditorRoute
import com.uit.finance.feature.accounts.presentation.list.AccountListRoute
import kotlinx.serialization.Serializable

/** Danh sách ví. */
@Serializable data object AccountsDestination

/** Tạo (`accountId == null`) hoặc sửa ví. */
@Serializable data class AccountEditorDestination(val accountId: String? = null)

/** Hợp đồng navigation: app quyết định đi *đâu* (mở editor, quay lại), feature quyết định *khi nào*. */
fun NavGraphBuilder.accountsNavGraph(
    onBack: () -> Unit,
    onOpenEditor: (accountId: String?) -> Unit,
) {
    composable<AccountsDestination> {
        AccountListRoute(onBack = onBack, onOpenEditor = onOpenEditor)
    }
    composable<AccountEditorDestination> { entry ->
        AccountEditorRoute(accountId = entry.toRoute<AccountEditorDestination>().accountId, onClose = onBack)
    }
}
