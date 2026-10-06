package com.uit.finance.feature.home.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.feature.home.presentation.navigation.HomeTarget
import org.koin.compose.viewmodel.koinViewModel

private const val NO_VALUE = "—"

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
    val spacing = FinanceTheme.spacing
    val open: (HomeTarget) -> Unit = { onIntent(HomeIntent.Open(it)) }
    val overview = state.overview

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item { Header() }
        item { BalanceCard(totalBalance = overview?.totalBalance, onClick = { open(HomeTarget.Accounts) }) }
        item { QuickActions(onOpen = open) }
        item {
            Section(title = "Tổng quan tháng này") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SummaryMetric("Tiền vào", overview?.monthIncome, Modifier.weight(1f))
                    SummaryMetric("Tiền ra", overview?.monthExpense, Modifier.weight(1f))
                }
            }
        }
        item {
            Section(title = "Lập kế hoạch") {
                FeatureEntry("Ngân sách", "Tiến độ ngân sách sẽ hiển thị tại đây") { open(HomeTarget.Budgets) }
                FeatureEntry("Khoản sắp đến hạn", "Các khoản đến hạn sẽ hiển thị tại đây") { open(HomeTarget.Bills) }
            }
        }
        item {
            Section(
                title = "Giao dịch gần đây",
                action = "Xem tất cả" to { open(HomeTarget.Transactions) },
            ) {
                EmptyCard(icon = FinanceIcons.Transactions, message = "Chưa có dữ liệu giao dịch để hiển thị")
            }
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(FinanceTheme.spacing.xs)) {
        Text("TÀI CHÍNH CỦA BẠN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Trang chủ", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Theo dõi mọi khoản tiền ở một nơi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BalanceCard(totalBalance: String?, onClick: () -> Unit) {
    val spacing = FinanceTheme.spacing
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("Tổng số dư", style = MaterialTheme.typography.titleMedium)
            ValueText(totalBalance, "Tổng số dư", MaterialTheme.typography.headlineLarge)
            if (totalBalance == null) {
                Text("Số dư sẽ hiển thị tại đây", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Xem các ví", style = MaterialTheme.typography.labelLarge)
                Icon(FinanceIcons.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun QuickActions(onOpen: (HomeTarget) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinanceTheme.spacing.sm)) {
        QuickAction("Thêm thu chi", FinanceIcons.Add, Modifier.weight(1f)) { onOpen(HomeTarget.AddTransaction) }
        QuickAction("Giao dịch", FinanceIcons.Transactions, Modifier.weight(1f)) { onOpen(HomeTarget.Transactions) }
        QuickAction("Báo cáo", FinanceIcons.Reports, Modifier.weight(1f)) { onOpen(HomeTarget.Reports) }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val spacing = FinanceTheme.spacing
    Card(onClick = onClick, modifier = modifier.heightIn(min = spacing.xxl)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md, horizontal = spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Section(
    title: String,
    action: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinanceTheme.spacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
        content()
    }
}

@Composable
private fun SummaryMetric(title: String, value: String?, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    Card(modifier = modifier) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ValueText(value, title, MaterialTheme.typography.titleLarge)
        }
    }
}

/** Số liệu chưa có thì hiện "—", nhưng TalkBack/VoiceOver đọc "<label> chưa có dữ liệu" thay vì "gạch ngang". */
@Composable
private fun ValueText(value: String?, label: String, style: TextStyle) {
    Text(
        text = value ?: NO_VALUE,
        style = style,
        modifier = if (value == null) Modifier.semantics { contentDescription = "$label chưa có dữ liệu" } else Modifier,
    )
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(FinanceIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptyCard(icon: ImageVector, message: String) {
    val spacing = FinanceTheme.spacing
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
