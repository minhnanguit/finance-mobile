package com.uit.finance.feature.transactions.presentation.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.EmptyState
import com.uit.finance.core.designsystem.component.FinanceFab
import com.uit.finance.core.designsystem.component.FinanceListItem
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MenuAction
import com.uit.finance.core.designsystem.component.MessageSnackbarHost
import com.uit.finance.core.designsystem.component.OverflowMenu
import com.uit.finance.core.designsystem.component.SectionHeader
import com.uit.finance.core.designsystem.component.SegmentedControl
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ui.ledger.categoryIconOrDefault
import com.uit.finance.core.ui.ledger.categoryTileColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun CategoriesRoute(
    initialKind: CategoryKind,
    onBack: () -> Unit,
    onOpenEditor: (categoryId: String?, kind: CategoryKind) -> Unit,
    viewModel: CategoriesViewModel = koinViewModel { parametersOf(CategoriesArgs(initialKind)) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CategoriesEffect.OpenEditor -> onOpenEditor(effect.categoryId, effect.kind)
                CategoriesEffect.NavigateBack -> onBack()
            }
        }
    }
    CategoriesScreen(state = state, onIntent = viewModel::onIntent)
}

/** Quản lý danh mục: tab Chi / Thu, danh mục đang dùng, danh mục đã lưu trữ thu gọn bên dưới. */
@Composable
internal fun CategoriesScreen(
    state: CategoriesState,
    onIntent: (CategoriesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    val horizontal = Modifier.padding(horizontal = spacing.lg)
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            FinanceTopBar(title = "Danh mục", onBack = { onIntent(CategoriesIntent.BackClicked) })
            SegmentedControl(
                options = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME),
                selected = state.kind,
                onSelect = { onIntent(CategoriesIntent.KindChanged(it)) },
                label = { if (it == CategoryKind.EXPENSE) "Chi" else "Thu" },
                modifier = horizontal,
            )
            when {
                state.isLoading -> LoadingIndicator()
                state.active.isEmpty() && state.archived.isEmpty() -> EmptyState(
                    icon = FinanceIcons.Categories,
                    title = "Chưa có danh mục",
                    message = "Danh mục mặc định sẽ có sau lần đồng bộ đầu tiên. Bạn cũng có thể tự tạo.",
                    actionLabel = "Tạo danh mục",
                    onAction = { onIntent(CategoriesIntent.AddClicked) },
                    modifier = horizontal,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Đáy chừa chỗ để nút + không che dòng cuối.
                    contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.xxl + spacing.xl),
                ) {
                    items(state.active, key = { it.id }) { item -> CategoryRow(item, onIntent, Modifier.animateItem()) }
                    if (state.archived.isNotEmpty()) {
                        item(key = "archived-header") {
                            SectionHeader(
                                title = "Đã lưu trữ (${state.archived.size})",
                                actionLabel = if (state.showArchived) "Ẩn" else "Hiện",
                                onAction = { onIntent(CategoriesIntent.ToggleArchived) },
                                modifier = Modifier.padding(top = spacing.md),
                            )
                        }
                        if (state.showArchived) {
                            items(state.archived, key = { it.id }) { item -> CategoryRow(item, onIntent, Modifier.animateItem()) }
                        }
                    }
                }
            }
        }
        FinanceFab(
            icon = FinanceIcons.Add,
            contentDescription = "Tạo danh mục",
            onClick = { onIntent(CategoriesIntent.AddClicked) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.lg),
        )
        MessageSnackbarHost(
            message = state.message?.resolve(),
            onShown = { onIntent(CategoriesIntent.MessageShown) },
            // Nằm trên nút +, để nút "Hoàn tác" không bị nút nổi che.
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = spacing.xxl + spacing.xl),
        )
    }
}

@Composable
private fun CategoryRow(item: CategoryItemUi, onIntent: (CategoriesIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = categoryTileColors(item.colorHex, item.kind)
    val actions = listOf(
        MenuAction("Sửa", FinanceIcons.Edit) { onIntent(CategoriesIntent.EditClicked(item.id)) },
        if (item.archived) {
            MenuAction("Bỏ lưu trữ", FinanceIcons.Unarchive) { onIntent(CategoriesIntent.ArchiveClicked(item.id, archived = false)) }
        } else {
            MenuAction("Lưu trữ", FinanceIcons.Archive) { onIntent(CategoriesIntent.ArchiveClicked(item.id, archived = true)) }
        },
    )
    FinanceListItem(
        title = item.name,
        subtitle = if (item.isDefault) "Mặc định" else null,
        leading = {
            IconTile(
                icon = categoryIconOrDefault(item.iconKey, item.kind),
                containerColor = colors.container,
                contentColor = colors.content,
            )
        },
        trailing = { OverflowMenu(actions = actions, contentDescription = "Thao tác với danh mục ${item.name}") },
        onClick = { onIntent(CategoriesIntent.EditClicked(item.id)) },
        modifier = modifier,
    )
}
