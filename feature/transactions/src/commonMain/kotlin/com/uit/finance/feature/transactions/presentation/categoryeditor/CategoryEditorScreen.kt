package com.uit.finance.feature.transactions.presentation.categoryeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.ChoiceTile
import com.uit.finance.core.designsystem.component.ColorSwatchPicker
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.FinanceTextField
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.IconTile
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SegmentedControl
import com.uit.finance.core.designsystem.icon.categoryIcon
import com.uit.finance.core.designsystem.icon.categoryIconChoices
import com.uit.finance.core.designsystem.theme.CategoryPalette
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ui.ledger.categoryIconOrDefault
import com.uit.finance.core.ui.ledger.categoryTileColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun CategoryEditorRoute(
    categoryId: String?,
    kind: CategoryKind,
    onClose: () -> Unit,
    viewModel: CategoryEditorViewModel = koinViewModel(key = "category-editor-$categoryId") {
        parametersOf(CategoryEditorArgs(categoryId, kind))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CategoryEditorEffect.Close -> onClose()
            }
        }
    }
    CategoryEditorScreen(state = state, onIntent = viewModel::onIntent)
}

/** Tạo / sửa danh mục: xem trước, tên, loại (chỉ khi tạo), icon, màu. */
@Composable
internal fun CategoryEditorScreen(
    state: CategoryEditorState,
    onIntent: (CategoryEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Column(modifier.fillMaxSize().imePadding()) {
        FinanceTopBar(
            title = if (state.isNew) "Tạo danh mục" else "Sửa danh mục",
            onBack = { onIntent(CategoryEditorIntent.BackClicked) },
        )
        if (state.isLoading) {
            LoadingIndicator()
            return@Column
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            state.error?.let { ErrorBanner(message = it, onDismiss = { onIntent(CategoryEditorIntent.ErrorDismissed) }) }
            val preview = categoryTileColors(state.colorHex, state.kind)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                IconTile(
                    icon = categoryIconOrDefault(state.iconKey, state.kind),
                    containerColor = preview.container,
                    contentColor = preview.content,
                )
                FinanceTextField(
                    value = state.name,
                    onValueChange = { onIntent(CategoryEditorIntent.NameChanged(it)) },
                    label = "Tên danh mục",
                    errorText = state.nameError?.resolve(),
                    imeAction = ImeAction.Done,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.isNew) {
                SegmentedControl(
                    options = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME),
                    selected = state.kind,
                    onSelect = { onIntent(CategoryEditorIntent.KindChanged(it)) },
                    label = { if (it == CategoryKind.EXPENSE) "Khoản chi" else "Khoản thu" },
                )
            }
            Label("Màu")
            ColorSwatchPicker(
                colors = CategoryPalette.hexes.mapNotNull { hex -> CategoryPalette.color(hex)?.let { hex to it } },
                selectedKey = state.colorHex?.uppercase(),
                onSelect = { onIntent(CategoryEditorIntent.ColorSelected(it)) },
            )
            Label("Biểu tượng")
            IconGrid(state, onIntent)
        }
        PrimaryButton(
            text = if (state.isNew) "Tạo danh mục" else "Lưu",
            onClick = { onIntent(CategoryEditorIntent.SaveClicked) },
            enabled = state.canSave,
            loading = state.isSaving,
            modifier = Modifier.padding(spacing.lg),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun IconGrid(state: CategoryEditorState, onIntent: (CategoryEditorIntent) -> Unit) {
    val spacing = FinanceTheme.spacing
    val colors = categoryTileColors(state.colorHex, state.kind)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        categoryIconChoices.withIndex().toList().chunked(ICON_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (index, key) ->
                    val icon = categoryIcon(key) ?: return@forEach
                    ChoiceTile(
                        icon = icon,
                        label = null,
                        contentDescription = "Biểu tượng ${index + 1}",
                        selected = key == state.iconKey,
                        onClick = { onIntent(CategoryEditorIntent.IconSelected(key)) },
                        containerColor = colors.container,
                        contentColor = colors.content,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(ICON_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val ICON_COLUMNS = 5
