package com.uit.finance.feature.transactions.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uit.finance.core.designsystem.component.ChoiceTile
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SelectableChip
import com.uit.finance.core.designsystem.component.SelectionField
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ui.ledger.categoryIconOrDefault
import com.uit.finance.core.ui.ledger.categoryTileColors
import com.uit.finance.core.ui.ledger.shortLabel
import com.uit.finance.feature.transactions.domain.model.DatePreset

/**
 * Sheet lọc: chạm là áp ngay (danh sách phía sau đổi theo), "Xong" chỉ đóng sheet. Khoảng ngày chọn một; loại, ví,
 * danh mục chọn nhiều.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryFilterSheet(state: HistoryState, onIntent: (HistoryIntent) -> Unit) {
    val spacing = FinanceTheme.spacing
    val filter = state.filter
    ModalBottomSheet(
        onDismissRequest = { onIntent(HistoryIntent.FilterSheetDismissed) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text("Bộ lọc", style = MaterialTheme.typography.titleLarge)

            FilterSection("Khoảng ngày") {
                ChipFlow {
                    DatePreset.entries.forEach { preset ->
                        SelectableChip(
                            text = preset.label(),
                            selected = filter.datePreset == preset,
                            onClick = { onIntent(HistoryIntent.DatePresetSelected(preset)) },
                        )
                    }
                }
                if (filter.datePreset == DatePreset.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        SelectionField(
                            label = "Từ ngày",
                            value = filter.customFrom?.toShortLabel() ?: "Không giới hạn",
                            onClick = { onIntent(HistoryIntent.DatePickerOpened(DateBound.From)) },
                            icon = FinanceIcons.Calendar,
                            modifier = Modifier.weight(1f),
                        )
                        SelectionField(
                            label = "Đến ngày",
                            value = filter.customTo?.toShortLabel() ?: "Không giới hạn",
                            onClick = { onIntent(HistoryIntent.DatePickerOpened(DateBound.To)) },
                            icon = FinanceIcons.Calendar,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            FilterSection("Loại") {
                ChipFlow {
                    FILTER_TYPES.forEach { type ->
                        SelectableChip(
                            text = type.shortLabel(),
                            selected = type in filter.types,
                            onClick = { onIntent(HistoryIntent.TypeToggled(type)) },
                        )
                    }
                }
            }

            if (state.accountOptions.isNotEmpty()) {
                FilterSection("Ví", caption = "Chuyển tiền tính cả ví gửi lẫn ví nhận.") {
                    ChipFlow {
                        state.accountOptions.forEach { account ->
                            SelectableChip(
                                text = if (account.archived) "${account.name} (đã lưu trữ)" else account.name,
                                selected = account.id in filter.accountIds,
                                onClick = { onIntent(HistoryIntent.AccountToggled(account.id)) },
                            )
                        }
                    }
                }
            }

            if (state.categoryOptions.isNotEmpty()) {
                FilterSection("Danh mục", caption = "Chọn danh mục cha là gồm luôn các danh mục con.") {
                    CategoryKind.entries.sortedBy { it != CategoryKind.EXPENSE }.forEach { kind ->
                        val options = state.categoryOptions.filter { it.kind == kind }
                        if (options.isNotEmpty()) {
                            Text(
                                text = if (kind == CategoryKind.EXPENSE) "Chi" else "Thu",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CategoryGrid(options, selected = filter.categoryIds, onIntent = onIntent)
                        }
                    }
                }
            }

            FilterSection("Trạng thái") {
                ChipFlow {
                    SelectableChip(
                        text = "Tất cả",
                        selected = !filter.draftsOnly,
                        onClick = { onIntent(HistoryIntent.DraftsOnlyChanged(false)) },
                    )
                    SelectableChip(
                        text = "Chỉ nháp",
                        selected = filter.draftsOnly,
                        onClick = { onIntent(HistoryIntent.DraftsOnlyChanged(true)) },
                    )
                }
            }

            PrimaryButton(text = "Xong", onClick = { onIntent(HistoryIntent.FilterSheetDismissed) })
        }
    }
}

@Composable
private fun FilterSection(title: String, caption: String? = null, content: @Composable () -> Unit) {
    val spacing = FinanceTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        caption?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    val spacing = FinanceTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) { content() }
}

/** Lưới 4 cột giống form giao dịch, nhưng chọn được nhiều ô. */
@Composable
private fun CategoryGrid(options: List<CategoryFilterOptionUi>, selected: Set<String>, onIntent: (HistoryIntent) -> Unit) {
    val spacing = FinanceTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        options.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    val colors = categoryTileColors(category.colorHex, category.kind)
                    ChoiceTile(
                        icon = categoryIconOrDefault(category.iconKey, category.kind),
                        label = category.name,
                        selected = category.id in selected,
                        onClick = { onIntent(HistoryIntent.CategoryToggled(category.id)) },
                        containerColor = colors.container,
                        contentColor = colors.content,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val GRID_COLUMNS = 4
