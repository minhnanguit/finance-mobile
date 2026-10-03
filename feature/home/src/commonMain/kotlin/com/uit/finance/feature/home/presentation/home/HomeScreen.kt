package com.uit.finance.feature.home.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.FinanceIcon
import com.uit.finance.core.designsystem.component.FinanceIconType
import com.uit.finance.core.designsystem.theme.FinanceTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun HomeRoute(
    onNavigate: (HomeTarget) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.Navigate -> onNavigate(effect.target)
            }
        }
    }
    HomeScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        LoadingIndicator(modifier)
        return
    }
    val spacing = FinanceTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = "TÀI CHÍNH CỦA BẠN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = "Trang chủ", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Theo dõi mọi khoản tiền ở một nơi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                onClick = { onIntent(HomeIntent.OpenWallets) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(text = "Tổng số dư", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { contentDescription = "Chưa có dữ liệu số dư" },
                    )
                    Text(
                        text = "Số dư sẽ hiển thị tại đây",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Xem các ví",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FinanceIcon(FinanceIconType.ChevronRight, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                QuickAction(
                    label = "Thêm thu chi",
                    icon = FinanceIconType.Add,
                    onClick = { onIntent(HomeIntent.AddTransaction) },
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    label = "Giao dịch",
                    icon = FinanceIconType.Transactions,
                    onClick = { onIntent(HomeIntent.OpenTransactions) },
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    label = "Báo cáo",
                    icon = FinanceIconType.Reports,
                    onClick = { onIntent(HomeIntent.OpenReports) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionHeading(title = "Tổng quan tháng này")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SummaryMetric(
                        title = "Tiền vào",
                        value = "—",
                        modifier = Modifier.weight(1f),
                    )
                    SummaryMetric(
                        title = "Tiền ra",
                        value = "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionHeading(title = "Lập kế hoạch")
                FeatureEntry(
                    title = "Ngân sách",
                    detail = "Tiến độ ngân sách sẽ hiển thị tại đây",
                    onClick = { onIntent(HomeIntent.OpenBudgets) },
                )
                FeatureEntry(
                    title = "Khoản sắp đến hạn",
                    detail = "Các khoản đến hạn sẽ hiển thị tại đây",
                    onClick = { onIntent(HomeIntent.OpenBills) },
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SectionHeading(
                    title = "Giao dịch gần đây",
                    action = "Xem tất cả",
                    onAction = { onIntent(HomeIntent.OpenTransactions) },
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FinanceIcon(FinanceIconType.Transactions, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = "Chưa có dữ liệu giao dịch để hiển thị",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: FinanceIconType, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    Card(onClick = onClick, modifier = modifier.heightIn(min = spacing.xxl)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md, horizontal = spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            FinanceIcon(icon, color = MaterialTheme.colorScheme.primary)
            Text(text = label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SummaryMetric(title: String, value: String, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { contentDescription = "$title chưa có dữ liệu" },
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun FeatureEntry(title: String, detail: String, onClick: () -> Unit) {
    val spacing = FinanceTheme.spacing
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FinanceIcon(FinanceIconType.ChevronRight, color = MaterialTheme.colorScheme.primary)
        }
    }
}
