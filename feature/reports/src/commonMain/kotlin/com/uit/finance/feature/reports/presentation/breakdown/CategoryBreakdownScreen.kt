package com.uit.finance.feature.reports.presentation.breakdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountText
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.SectionHeader
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.feature.reports.presentation.common.ReportDonut
import com.uit.finance.feature.reports.presentation.common.ReportShareRow
import com.uit.finance.feature.reports.presentation.navigation.CategoryBreakdownDestination
import com.uit.finance.feature.reports.presentation.navigation.ReportTransactionsQuery
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun CategoryBreakdownRoute(
    destination: CategoryBreakdownDestination,
    onBack: () -> Unit,
    onOpenTransactions: (ReportTransactionsQuery) -> Unit,
    viewModel: CategoryBreakdownViewModel = koinViewModel(key = "report-breakdown-$destination") {
        parametersOf(
            CategoryBreakdownArgs(destination.categoryId, destination.toPeriod(), destination.toKind(), destination.currency),
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CategoryBreakdownEffect.OpenTransactions -> onOpenTransactions(effect.query)
                CategoryBreakdownEffect.NavigateBack -> onBack()
            }
        }
    }
    CategoryBreakdownScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun CategoryBreakdownScreen(
    state: CategoryBreakdownState,
    onIntent: (CategoryBreakdownIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    Column(modifier.fillMaxSize()) {
        FinanceTopBar(title = state.title, onBack = { onIntent(CategoryBreakdownIntent.Back) })
        if (state.isLoading) {
            LoadingIndicator()
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item(key = "headline") {
                Column(horizontal, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "${if (state.kind == CategoryKind.EXPENSE) "Chi" else "Thu"} · ${state.periodLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AmountText(
                        amount = state.total,
                        currency = state.currency,
                        style = MaterialTheme.typography.headlineLarge,
                        currencyStyle = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = FinanceIcons.Reports,
                        title = "Không có giao dịch",
                        message = "Danh mục này không có giao dịch nào trong kỳ.",
                        modifier = horizontal.padding(top = spacing.lg),
                    )
                }
                return@LazyColumn
            }
            item(key = "donut") {
                ReportDonut(
                    slices = state.slices,
                    centerValue = state.totalCompact,
                    centerLabel = state.title,
                    description = state.donutDescription,
                    animationKey = state.animationKey,
                    modifier = horizontal.padding(vertical = spacing.md),
                )
            }
            items(state.rows, key = { it.key }) { row ->
                ReportShareRow(row = row, onClick = { onIntent(CategoryBreakdownIntent.OpenRow(row)) }, modifier = horizontal)
            }
            item(key = "all") {
                SectionHeader(
                    title = "",
                    actionLabel = "Xem tất cả giao dịch",
                    onAction = { onIntent(CategoryBreakdownIntent.OpenAll) },
                    modifier = horizontal.padding(top = spacing.sm),
                )
            }
        }
    }
}
