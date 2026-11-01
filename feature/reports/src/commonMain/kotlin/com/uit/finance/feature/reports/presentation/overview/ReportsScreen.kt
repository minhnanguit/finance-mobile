package com.uit.finance.feature.reports.presentation.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.designsystem.component.AmountText
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MenuAction
import com.uit.finance.core.designsystem.component.OptionSheet
import com.uit.finance.core.designsystem.component.OverflowMenu
import com.uit.finance.core.designsystem.component.PeriodSwitcher
import com.uit.finance.core.designsystem.component.SectionHeader
import com.uit.finance.core.designsystem.component.SegmentedControl
import com.uit.finance.core.designsystem.component.SheetOption
import com.uit.finance.core.designsystem.component.TrendBar
import com.uit.finance.core.designsystem.component.TrendBarChart
import com.uit.finance.core.designsystem.component.TrendBarStyle
import com.uit.finance.core.designsystem.component.swipeToChangePeriod
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.feature.reports.presentation.common.ReportDonut
import com.uit.finance.feature.reports.presentation.common.ReportShareRow
import com.uit.finance.feature.reports.presentation.navigation.CategoryBreakdownDestination
import com.uit.finance.feature.reports.presentation.navigation.ReportTransactionsQuery
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ReportsRoute(
    onBack: () -> Unit,
    onOpenBreakdown: (CategoryBreakdownDestination) -> Unit,
    onOpenTransactions: (ReportTransactionsQuery) -> Unit,
    onAddTransaction: () -> Unit,
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReportsEffect.OpenBreakdown -> onOpenBreakdown(effect.destination)
                is ReportsEffect.OpenTransactions -> onOpenTransactions(effect.query)
                ReportsEffect.AddTransaction -> onAddTransaction()
                ReportsEffect.NavigateBack -> onBack()
            }
        }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(ReportsIntent.Resumed)
        onPauseOrDispose { }
    }
    ReportsScreen(state = state, onIntent = viewModel::onIntent)
}

/**
 * Màn Báo cáo (docs/REPORTS-PLAN.md mục 1): số chính → donut + danh sách → cột 6 tháng. Trên màn chỉ có ‹ kỳ › và
 * Chi/Thu; Tháng/Năm, Tiền tệ ở menu. Vuốt ngang để đổi kỳ.
 */
@Composable
internal fun ReportsScreen(state: ReportsState, onIntent: (ReportsIntent) -> Unit, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    Column(modifier.fillMaxSize()) {
        FinanceTopBar(
            title = "Báo cáo",
            onBack = { onIntent(ReportsIntent.Back) },
            actions = { ReportsMenu(state, onIntent) },
        )
        if (state.isLoading) {
            LoadingIndicator()
            return@Column
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .swipeToChangePeriod(
                    canGoNext = state.canGoNext,
                    onPrevious = { onIntent(ReportsIntent.Previous) },
                    onNext = { onIntent(ReportsIntent.Next) },
                ),
            contentPadding = PaddingValues(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            item(key = "period") {
                PeriodSwitcher(
                    label = state.periodLabel,
                    canGoNext = state.canGoNext,
                    onPrevious = { onIntent(ReportsIntent.Previous) },
                    onNext = { onIntent(ReportsIntent.Next) },
                    modifier = horizontal,
                )
            }
            item(key = "kind") {
                SegmentedControl(
                    options = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME),
                    selected = state.kind,
                    onSelect = { onIntent(ReportsIntent.SelectKind(it)) },
                    label = { if (it == CategoryKind.EXPENSE) "Chi" else "Thu" },
                    modifier = horizontal.padding(top = spacing.sm),
                )
            }
            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = FinanceIcons.Reports,
                        title = "Chưa có giao dịch",
                        message = "Kỳ này chưa có khoản thu chi nào. Ghi một khoản để xem báo cáo.",
                        actionLabel = "Thêm giao dịch",
                        onAction = { onIntent(ReportsIntent.AddTransaction) },
                        modifier = horizontal.padding(top = spacing.lg),
                    )
                }
                return@LazyColumn
            }
            item(key = "headline") { Headline(state, horizontal.padding(top = spacing.md, bottom = spacing.sm)) }
            if (state.isKindEmpty) {
                item(key = "kind-empty") {
                    Text(
                        text = if (state.kind == CategoryKind.EXPENSE) "Kỳ này chưa có khoản chi nào." else "Kỳ này chưa có khoản thu nào.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = horizontal.padding(vertical = spacing.lg),
                    )
                }
            } else {
                item(key = "donut") {
                    ReportDonut(
                        slices = state.slices,
                        centerValue = state.totalCompact,
                        centerLabel = if (state.kind == CategoryKind.EXPENSE) "Tổng chi" else "Tổng thu",
                        description = state.donutDescription,
                        animationKey = state.animationKey,
                        modifier = horizontal.padding(vertical = spacing.md),
                    )
                }
                items(state.rows, key = { "row-${it.key}" }) { row ->
                    ReportShareRow(
                        row = row,
                        onClick = { onIntent(ReportsIntent.OpenRow(row)) },
                        expanded = state.showOthers,
                        modifier = Modifier.animateItem().then(horizontal),
                    )
                }
                if (state.showOthers) {
                    items(state.otherRows, key = { "other-${it.key}" }) { row ->
                        ReportShareRow(
                            row = row,
                            onClick = { onIntent(ReportsIntent.OpenRow(row)) },
                            modifier = Modifier.animateItem().then(horizontal).padding(start = spacing.md),
                        )
                    }
                }
            }
            item(key = "trend") { Trend(state, onIntent, horizontal.padding(top = spacing.lg)) }
        }
    }
    if (state.showCurrencySheet) {
        OptionSheet(
            title = "Tiền tệ",
            options = state.currencyOptions.map { SheetOption(key = it, value = it, title = it) },
            selected = state.currency,
            onSelect = { onIntent(ReportsIntent.SelectCurrency(it)) },
            onDismiss = { onIntent(ReportsIntent.DismissCurrencyPicker) },
        )
    }
}

@Composable
private fun ReportsMenu(state: ReportsState, onIntent: (ReportsIntent) -> Unit) {
    val actions = buildList {
        add(
            if (state.mode == ReportMode.Month) {
                MenuAction("Xem theo năm", FinanceIcons.Calendar) { onIntent(ReportsIntent.SelectMode(ReportMode.Year)) }
            } else {
                MenuAction("Xem theo tháng", FinanceIcons.Calendar) { onIntent(ReportsIntent.SelectMode(ReportMode.Month)) }
            },
        )
        if (state.showCurrencyOption) {
            add(MenuAction("Tiền tệ: ${state.currency}", FinanceIcons.Coins) { onIntent(ReportsIntent.OpenCurrencyPicker) })
        }
    }
    if (!state.isLoading) OverflowMenu(actions = actions, contentDescription = "Tuỳ chọn báo cáo")
}

/** Số chính to nhất (V1), một dòng so sánh, và dòng phụ nhỏ "Thu 25tr · Còn lại 6,6tr". */
@Composable
private fun Headline(state: ReportsState, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    val colors = FinanceTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        AmountText(
            amount = state.total,
            currency = state.currency,
            style = MaterialTheme.typography.headlineLarge,
            currencyStyle = MaterialTheme.typography.titleSmall,
        )
        state.comparison?.let { comparison ->
            val color = when {
                comparison.direction == ChangeDirection.Flat -> MaterialTheme.colorScheme.onSurfaceVariant
                comparison.isGood -> colors.income
                else -> colors.expense
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                when (comparison.direction) {
                    ChangeDirection.Up -> Icon(FinanceIcons.MoveUp, contentDescription = "Tăng", tint = color, modifier = Modifier.size(16.dp))
                    ChangeDirection.Down -> Icon(FinanceIcons.MoveDown, contentDescription = "Giảm", tint = color, modifier = Modifier.size(16.dp))
                    ChangeDirection.Flat -> Unit
                }
                Text(comparison.text, style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
        Text(
            text = buildAnnotatedString {
                append(state.counterpartLabel)
                append(" · ")
                if (state.remainingNegative) {
                    withStyle(SpanStyle(color = colors.expense)) { append(state.remainingLabel) }
                } else {
                    append(state.remainingLabel)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Trend(state: ReportsState, onIntent: (ReportsIntent) -> Unit, modifier: Modifier = Modifier) {
    // Nhãn trục là chi tiết vẽ: Vico hỏi giá trị bất kỳ, nên format ngay tại đây thay vì đưa sẵn vào state.
    val formatter = remember { MoneyFormatter() }
    val currency = state.currency
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FinanceTheme.spacing.sm)) {
        SectionHeader(title = state.trendTitle)
        TrendBarChart(
            bars = state.trend.map { bar ->
                TrendBar(
                    label = bar.label,
                    value = bar.value,
                    style = when {
                        bar.isSelected -> TrendBarStyle.Selected
                        bar.isPartial || bar.isFuture -> TrendBarStyle.Partial
                        else -> TrendBarStyle.Normal
                    },
                    actionLabel = bar.actionLabel,
                )
            },
            color = if (state.kind == CategoryKind.EXPENSE) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            axisLabel = { formatter.formatCompact(Money(it.toLong(), currency)) },
            contentDescription = state.trendDescription,
            onSelect = { onIntent(ReportsIntent.SelectTrendBar(it)) },
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}
