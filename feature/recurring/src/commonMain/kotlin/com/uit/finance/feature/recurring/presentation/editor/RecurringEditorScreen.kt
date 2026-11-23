package com.uit.finance.feature.recurring.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uit.finance.core.designsystem.component.AmountField
import com.uit.finance.core.designsystem.component.ConfirmDialog
import com.uit.finance.core.designsystem.component.DatePickerSheet
import com.uit.finance.core.designsystem.component.ErrorBanner
import com.uit.finance.core.designsystem.component.FinanceTextField
import com.uit.finance.core.designsystem.component.FinanceTopBar
import com.uit.finance.core.designsystem.component.LoadingIndicator
import com.uit.finance.core.designsystem.component.MenuAction
import com.uit.finance.core.designsystem.component.OptionSheet
import com.uit.finance.core.designsystem.component.OverflowMenu
import com.uit.finance.core.designsystem.component.PrimaryButton
import com.uit.finance.core.designsystem.component.SegmentedControl
import com.uit.finance.core.designsystem.component.SelectionField
import com.uit.finance.core.designsystem.component.SheetOption
import com.uit.finance.core.designsystem.icon.FinanceIcons
import com.uit.finance.core.designsystem.theme.FinanceTheme
import com.uit.finance.core.recurring.domain.model.Frequency
import com.uit.finance.core.recurring.domain.model.RecurringType
import com.uit.finance.feature.recurring.presentation.dayLabel
import com.uit.finance.feature.recurring.presentation.remindLabel
import com.uit.finance.feature.recurring.presentation.shortLabel
import com.uit.finance.feature.recurring.presentation.weekdayLabel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun RecurringEditorRoute(
    recurringId: String?,
    onClose: () -> Unit,
    viewModel: RecurringEditorViewModel = koinViewModel(key = "recurring-editor-$recurringId") {
        parametersOf(RecurringEditorArgs(recurringId))
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RecurringEditorEffect.Close -> onClose()
            }
        }
    }
    RecurringEditorScreen(state = state, onIntent = viewModel::onIntent)
}

/** Tạo / sửa khoản định kỳ, xem trước 5 kỳ tới; kết thúc và xoá qua menu ⋮. */
@Composable
internal fun RecurringEditorScreen(
    state: RecurringEditorState,
    onIntent: (RecurringEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinanceTheme.spacing
    Column(modifier.fillMaxSize()) {
        FinanceTopBar(
            title = if (state.isNew) "Thêm khoản định kỳ" else "Sửa khoản định kỳ",
            onBack = { onIntent(RecurringEditorIntent.BackClicked) },
            actions = {
                if (!state.isNew && !state.isDeletedElsewhere) {
                    OverflowMenu(
                        actions = buildList {
                            if (!state.isEnded) {
                                add(MenuAction("Kết thúc từ hôm nay", FinanceIcons.Archive) { onIntent(RecurringEditorIntent.EndClicked) })
                            }
                            add(MenuAction("Xoá", FinanceIcons.Delete, destructive = true) { onIntent(RecurringEditorIntent.DeleteClicked) })
                        },
                        contentDescription = "Thao tác với khoản định kỳ",
                    )
                }
            },
        )
        if (state.isLoading) {
            LoadingIndicator()
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            state.error?.let { ErrorBanner(message = it, onDismiss = { onIntent(RecurringEditorIntent.ErrorDismissed) }) }
            if (state.isEnded) Hint("Khoản này đã kết thúc. Muốn chạy lại thì tạo khoản mới.")
            if (state.isNew) {
                SegmentedControl(
                    options = RecurringType.entries,
                    selected = state.type,
                    onSelect = { onIntent(RecurringEditorIntent.TypeChanged(it)) },
                    label = { if (it == RecurringType.EXPENSE) "Khoản chi" else "Khoản thu" },
                )
            }
            FinanceTextField(
                value = state.name,
                onValueChange = { onIntent(RecurringEditorIntent.NameChanged(it)) },
                label = "Tên (ví dụ Tiền nhà, Spotify)",
                errorText = state.nameError?.resolve(),
            )
            AmountField(
                value = state.amount,
                onValueChange = { onIntent(RecurringEditorIntent.AmountChanged(it)) },
                currency = state.currency,
                label = "Số tiền dự kiến mỗi kỳ",
                errorText = state.amountError?.resolve(),
            )
            SelectionField(
                label = "Ví",
                value = state.accountName ?: "Chọn ví",
                icon = FinanceIcons.Wallet,
                onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.ACCOUNT)) },
            )
            SelectionField(
                label = "Danh mục",
                value = state.categoryName ?: "Chọn danh mục",
                icon = FinanceIcons.Categories,
                onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.CATEGORY)) },
            )
            ScheduleSection(state, onIntent)
            if (state.isNew) {
                SelectionField(
                    label = "Bắt đầu từ",
                    value = state.startDate?.shortLabel().orEmpty(),
                    icon = FinanceIcons.Calendar,
                    onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.START_DATE)) },
                )
                SelectionField(
                    label = "Kết thúc",
                    value = state.endDate?.shortLabel() ?: "Không kết thúc",
                    icon = FinanceIcons.Calendar,
                    onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.END_DATE)) },
                )
            } else {
                SelectionField(
                    label = "Thay đổi lịch và số tiền áp dụng từ",
                    value = state.effectiveFrom?.shortLabel().orEmpty(),
                    icon = FinanceIcons.Calendar,
                    onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.EFFECTIVE_DATE)) },
                )
                if (state.effectiveFromWarning) {
                    Hint(
                        "Kỳ này đã được ghi theo lịch cũ. Áp dụng từ ${state.effectiveFrom?.shortLabel()} có thể ghi thêm một " +
                            "kỳ nữa; ngày gợi ý là ${state.suggestedEffectiveFrom?.shortLabel()}.",
                        warning = true,
                    )
                }
            }
            if (state.preview.isNotEmpty()) {
                Hint("Các kỳ tới: ${state.preview.joinToString(", ")}")
            }
            SelectionField(
                label = "Nhắc trước",
                value = remindLabel(state.remindDaysBefore),
                icon = FinanceIcons.Bell,
                onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.REMIND)) },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tự xác nhận", style = MaterialTheme.typography.titleSmall)
                    Hint("Ghi luôn vào sổ thay vì để nháp. Kỳ ghi bù vẫn luôn là nháp.")
                }
                Switch(checked = state.autoConfirm, onCheckedChange = { onIntent(RecurringEditorIntent.AutoConfirmChanged(it)) })
            }
            FinanceTextField(
                value = state.note,
                onValueChange = { onIntent(RecurringEditorIntent.NoteChanged(it)) },
                label = "Ghi chú (không bắt buộc)",
                singleLine = false,
            )
            Hint("Đến hạn, app tự ghi một giao dịch nháp để bạn xác nhận. Thông báo nhắc chỉ hiện tên và ngày, không hiện số tiền.")
        }
        PrimaryButton(
            text = if (state.isNew) "Thêm khoản định kỳ" else "Lưu",
            onClick = { onIntent(RecurringEditorIntent.SaveClicked) },
            enabled = state.canSave,
            loading = state.isSaving,
            modifier = Modifier.padding(spacing.lg),
        )
    }

    Pickers(state, onIntent)

    state.backfillPrompt?.let { count ->
        AlertDialog(
            onDismissRequest = { onIntent(RecurringEditorIntent.BackfillDismissed) },
            title = { Text("Ghi các kỳ đã qua?") },
            text = {
                Text(
                    "Ngày bắt đầu nằm trong quá khứ: có $count kỳ trong 90 ngày qua. Ghi chúng thành giao dịch nháp để bạn " +
                        "xác nhận, hay chỉ bắt đầu ghi từ hôm nay?",
                )
            },
            confirmButton = { TextButton(onClick = { onIntent(RecurringEditorIntent.BackfillAnswered(true)) }) { Text("Ghi $count kỳ nháp") } },
            dismissButton = { TextButton(onClick = { onIntent(RecurringEditorIntent.BackfillAnswered(false)) }) { Text("Chỉ từ hôm nay") } },
        )
    }

    if (state.endConfirmVisible) {
        ConfirmDialog(
            title = "Kết thúc \"${state.name}\"?",
            message = "Không tạo kỳ nào sau hôm nay nữa. Giao dịch đã ghi giữ nguyên. Khoản đã kết thúc không mở lại được.",
            confirmLabel = "Kết thúc",
            dismissLabel = "Huỷ",
            destructive = true,
            onConfirm = { onIntent(RecurringEditorIntent.EndConfirmed) },
            onDismiss = { onIntent(RecurringEditorIntent.EndDismissed) },
        )
    }

    if (state.deleteConfirmVisible) {
        ConfirmDialog(
            title = "Xoá \"${state.name}\"?",
            message = "Không tạo kỳ mới nữa. Giao dịch đã ghi vẫn giữ nguyên trong sổ.",
            confirmLabel = "Xoá",
            dismissLabel = "Huỷ",
            destructive = true,
            onConfirm = { onIntent(RecurringEditorIntent.DeleteConfirmed) },
            onDismiss = { onIntent(RecurringEditorIntent.DeleteDismissed) },
        )
    }
}

@Composable
private fun ScheduleSection(state: RecurringEditorState, onIntent: (RecurringEditorIntent) -> Unit) {
    val spacing = FinanceTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text("Lặp lại", style = MaterialTheme.typography.titleSmall)
        SegmentedControl(
            options = Frequency.entries,
            selected = state.frequency,
            onSelect = { onIntent(RecurringEditorIntent.FrequencyChanged(it)) },
            label = {
                when (it) {
                    Frequency.WEEKLY -> "Hằng tuần"
                    Frequency.MONTHLY -> "Hằng tháng"
                    Frequency.YEARLY -> "Hằng năm"
                }
            },
        )
        val unit = when (state.frequency) {
            Frequency.WEEKLY -> "tuần"
            Frequency.MONTHLY -> "tháng"
            Frequency.YEARLY -> "năm"
        }
        SelectionField(
            label = "Chu kỳ",
            value = if (state.interval == 1) "Mỗi $unit" else "Mỗi ${state.interval} $unit",
            icon = FinanceIcons.Refresh,
            onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.INTERVAL)) },
        )
        when (state.frequency) {
            Frequency.WEEKLY -> SelectionField(
                label = "Vào",
                value = weekdayLabel(state.weekday),
                icon = FinanceIcons.Calendar,
                onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.WEEKDAY)) },
            )
            Frequency.MONTHLY -> SelectionField(
                label = "Vào",
                value = dayLabel(state.dayOfMonth),
                icon = FinanceIcons.Calendar,
                onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.DAY)) },
            )
            Frequency.YEARLY -> Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SelectionField(
                    label = "Tháng",
                    value = "Tháng ${state.month}",
                    onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.MONTH)) },
                    modifier = Modifier.weight(1f),
                )
                SelectionField(
                    label = "Ngày",
                    value = dayLabel(state.dayOfMonth),
                    onClick = { onIntent(RecurringEditorIntent.PickerOpened(EditorPicker.DAY)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.frequency != Frequency.WEEKLY && state.dayOfMonth in 29..30) {
            Hint("Tháng không có ngày ${state.dayOfMonth} thì ghi vào ngày cuối tháng.")
        }
    }
}

@Composable
private fun Pickers(state: RecurringEditorState, onIntent: (RecurringEditorIntent) -> Unit) {
    val picker = state.picker ?: return
    val today = state.today ?: return
    val dismiss = { onIntent(RecurringEditorIntent.PickerDismissed) }
    when (picker) {
        EditorPicker.ACCOUNT -> Sheet("Chọn ví", state.accountOptions, state.accountId, picker, onIntent)
        EditorPicker.CATEGORY -> Sheet("Chọn danh mục", state.categoryOptions, state.categoryId, picker, onIntent)
        EditorPicker.INTERVAL -> Sheet("Chu kỳ", (1..12).map { OptionUi("$it", if (it == 1) "Mỗi kỳ" else "Mỗi $it kỳ") }, "${state.interval}", picker, onIntent)
        EditorPicker.WEEKDAY -> Sheet("Thứ", (1..7).map { OptionUi("$it", weekdayLabel(it)) }, "${state.weekday}", picker, onIntent)
        EditorPicker.DAY -> Sheet("Ngày trong tháng", (1..31).map { OptionUi("$it", dayLabel(it)) }, "${state.dayOfMonth}", picker, onIntent)
        EditorPicker.MONTH -> Sheet("Tháng", (1..12).map { OptionUi("$it", "Tháng $it") }, "${state.month}", picker, onIntent)
        EditorPicker.REMIND -> Sheet(
            "Nhắc trước",
            listOf(OptionUi("off", remindLabel(null))) + (0..7).map { OptionUi("$it", remindLabel(it)) },
            state.remindDaysBefore?.toString() ?: "off",
            picker,
            onIntent,
        )
        EditorPicker.START_DATE -> DatePickerSheet(
            selected = state.startDate ?: today,
            range = LocalDate(2000, 1, 1)..today.plus(DatePeriod(days = 366)),
            onSelect = { onIntent(RecurringEditorIntent.DateSelected(picker, it)) },
            onDismiss = dismiss,
        )
        EditorPicker.EFFECTIVE_DATE -> DatePickerSheet(
            selected = state.effectiveFrom ?: today,
            range = today..today.plus(DatePeriod(days = 366)),
            onSelect = { onIntent(RecurringEditorIntent.DateSelected(picker, it)) },
            onDismiss = dismiss,
        )
        EditorPicker.END_DATE -> DatePickerSheet(
            selected = state.endDate ?: maxOf(today, state.startDate ?: today),
            range = today.minus(DatePeriod(days = 1))..today.plus(DatePeriod(years = 10)),
            onSelect = { onIntent(RecurringEditorIntent.DateSelected(picker, it)) },
            onDismiss = dismiss,
        )
    }
}

@Composable
private fun Sheet(
    title: String,
    options: List<OptionUi>,
    selected: String?,
    picker: EditorPicker,
    onIntent: (RecurringEditorIntent) -> Unit,
) {
    OptionSheet(
        title = title,
        options = options.map { SheetOption(key = it.id, value = it.id, title = it.title) },
        selected = selected,
        onSelect = { onIntent(RecurringEditorIntent.OptionSelected(picker, it)) },
        onDismiss = { onIntent(RecurringEditorIntent.PickerDismissed) },
    )
}

@Composable
private fun Hint(text: String, warning: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) FinanceTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
