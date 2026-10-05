package com.uit.finance.feature.home.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.uit.finance.feature.home.presentation.home.HomeRoute
import kotlinx.serialization.Serializable

/** Destination type-safe của feature home. `composeApp` gắn vào NavHost. */
@Serializable data object HomeDestination

/**
 * Những nơi người dùng muốn đi từ Home. Màn đích thuộc feature khác (transactions, accounts…),
 * nên home chỉ nói *ý định*; `composeApp` map sang destination thật.
 */
enum class HomeTarget { AddTransaction, Transactions, Accounts, Budgets, Reports, Bills }

/** Hợp đồng navigation của feature: app quyết định đi *đâu*, feature quyết định *khi nào*. */
fun NavGraphBuilder.homeNavGraph(onNavigate: (HomeTarget) -> Unit) {
    composable<HomeDestination> { HomeRoute(onNavigate = onNavigate) }
}
