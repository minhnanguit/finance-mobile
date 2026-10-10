package com.uit.finance.feature.home.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AccountCard
import com.uit.finance.core.designsystem.component.AmountText
import com.uit.finance.core.designsystem.component.Avatar
import com.uit.finance.core.designsystem.component.CircleIconButton
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceListItem
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.PagerIndicator
import com.uit.finance.core.designsystem.component.QuickActionButton
import com.uit.finance.core.designsystem.component.SectionHeader
import com.uit.finance.core.designsystem.component.StatTile
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.feature.home.presentation.navigation.HomeTarget
import kotlin.math.absoluteValue
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
    // Quay lại màn hình sau nửa đêm / đầu tháng: "Tháng này" và "Hôm nay" phải theo ngày mới.
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(HomeIntent.Refresh)
        onPauseOrDispose { }
    }
    HomeScreen(state = state, onIntent = viewModel::onIntent)
}

/**
 * Bố cục theo bản thiết kế tham chiếu: lời chào → pager thẻ ví → 4 hành động nhanh → thu/chi tháng này
 * → giao dịch gần đây. Mọi thành phần lấy từ `core/designsystem`; màn hình chỉ xếp chỗ.
 */
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
    val open: (HomeTarget) -> Unit = { onIntent(HomeIntent.Open(it)) }
    val pagerState = rememberPagerState(pageCount = { state.accounts.size })
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    // Thu/chi hiện theo tiền tệ của thẻ đang xem; chưa có ví thì lấy tiền tệ nào có phát sinh.
    val visibleCurrency = state.accounts.getOrNull(pagerState.currentPage)?.currency
        ?: state.monthByCurrency.keys.firstOrNull()
    val monthSummary = visibleCurrency?.let(state.monthByCurrency::get)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = spacing.md, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item(key = "header") {
            HomeHeader(
                userName = state.userName,
                todayLabel = state.todayLabel,
                onBell = { open(HomeTarget.Bills) },
                modifier = horizontal,
            )
        }
        item(key = "accounts") {
            AccountsPager(
                accounts = state.accounts,
                pagerState = pagerState,
                onOpenAccounts = { open(HomeTarget.Accounts) },
            )
        }
        item(key = "actions") { QuickActions(onOpen = open, modifier = horizontal) }
        if (monthSummary != null) {
            item(key = "month") { MonthSummary(summary = monthSummary, modifier = horizontal) }
        }
        item(key = "recent-header") {
            SectionHeader(
                title = "Giao dịch gần đây",
                actionLabel = "Xem tất cả",
                onAction = { open(HomeTarget.Transactions) },
                modifier = horizontal,
            )
        }
        if (state.recentTransactions.isEmpty()) {
            item(key = "recent-empty") {
                EmptyState(
                    icon = FinanceIcons.Receipt,
                    title = "Chưa có giao dịch",
                    message = "Ghi khoản thu chi đầu tiên để thấy lịch sử ở đây.",
                    actionLabel = "Thêm giao dịch",
                    onAction = { open(HomeTarget.AddTransaction) },
                    modifier = horizontal,
                )
            }
        } else {
            items(state.recentTransactions, key = { it.id }) { row ->
                TransactionRow(
                    row = row,
                    onClick = { open(HomeTarget.Transactions) },
                    modifier = Modifier.animateItem().then(horizontal),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    userName: String?,
    todayLabel: String,
    onBell: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = userName)
        Column(Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    append("Xin chào")
                    if (!userName.isNullOrBlank()) {
                        append(", ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(userName) }
                    }
                    append("!")
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            if (todayLabel.isNotEmpty()) {
                Text(
                    text = todayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CircleIconButton(icon = FinanceIcons.Bell, contentDescription = "Khoản sắp đến hạn", onClick = onBell)
    }
}

@Composable
private fun AccountsPager(
    accounts: List<AccountCardUi>,
    pagerState: PagerState,
    onOpenAccounts: () -> Unit,
) {
    val spacing = FinanceTheme.spacing
    if (accounts.isEmpty()) {
        EmptyState(
            icon = FinanceIcons.Wallet,
            title = "Chưa có ví nào",
            message = "Tạo ví đầu tiên (tiền mặt, ngân hàng, ví điện tử) để bắt đầu ghi chép.",
            actionLabel = "Tạo ví",
            onAction = onOpenAccounts,
            modifier = Modifier.padding(horizontal = spacing.lg),
        )
        return
    }
    val cardColors = FinanceTheme.colors.accountCards
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(start = spacing.lg, end = spacing.xxl),
            pageSpacing = spacing.sm,
            beyondViewportPageCount = 1,
        ) { page ->
            val account = accounts[page]
            AccountCard(
                name = account.name,
                balance = account.balance,
                currency = account.currency,
                colors = cardColors[page % cardColors.size],
                typeLabel = account.type.label(),
                icon = account.type.icon(),
                patternSeed = account.id.hashCode(),
                onClick = onOpenAccounts,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .graphicsLayer {
                        // Thẻ kế bên hơi nhỏ và mờ, trượt vào giữa thì về 100% — đọc offset lúc vẽ nên không recompose.
                        val distance = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue
                            .coerceIn(0f, 1f)
                        val focus = 1f - distance
                        scaleY = lerp(0.94f, 1f, focus)
                        alpha = lerp(0.7f, 1f, focus)
                    },
            )
        }
        if (accounts.size > 1) {
            PagerIndicator(pageCount = accounts.size, currentPage = pagerState.currentPage)
        }
    }
}

@Composable
private fun QuickActions(onOpen: (HomeTarget) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickActionButton(FinanceIcons.Add, "Thêm", { onOpen(HomeTarget.AddTransaction) }, Modifier.weight(1f))
        QuickActionButton(FinanceIcons.Wallet, "Ví", { onOpen(HomeTarget.Accounts) }, Modifier.weight(1f))
        QuickActionButton(FinanceIcons.Reports, "Báo cáo", { onOpen(HomeTarget.Reports) }, Modifier.weight(1f))
        QuickActionButton(FinanceIcons.Budget, "Ngân sách", { onOpen(HomeTarget.Budgets) }, Modifier.weight(1f))
    }
}

@Composable
private fun MonthSummary(summary: MonthSummaryUi, modifier: Modifier = Modifier) {
    val spacing = FinanceTheme.spacing
    val colors = FinanceTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeader(title = "Tháng này")
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StatTile(
                label = "Tiền vào",
                amount = summary.income,
                currency = summary.currency,
                icon = FinanceIcons.Income,
                iconContainerColor = colors.incomeContainer,
                iconColor = colors.income,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Tiền ra",
                amount = summary.expense,
                currency = summary.currency,
                icon = FinanceIcons.Expense,
                iconContainerColor = colors.expenseContainer,
                iconColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TransactionRow(row: TransactionRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val amountColor = when (row.type) {
        TransactionType.INCOME -> FinanceTheme.colors.income
        TransactionType.EXPENSE, TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurface
    }
    FinanceListItem(
        title = row.title,
        subtitle = row.subtitle,
        leading = {
            IconTile(
                icon = row.icon(),
                containerColor = row.type.tileContainer(),
                contentColor = row.type.tileContent(),
            )
        },
        trailing = {
            AmountText(amount = row.amount, currency = row.currency, sign = row.sign, amountColor = amountColor)
        },
        onClick = onClick,
        modifier = modifier,
    )
}
