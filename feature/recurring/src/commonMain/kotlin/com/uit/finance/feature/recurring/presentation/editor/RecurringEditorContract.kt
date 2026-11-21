package com.uit.finance.feature.recurring.presentation.editor

import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.recurring.domain.model.Frequency
import com.uit.finance.core.recurring.domain.model.RecurringType
import kotlinx.datetime.LocalDate

/** Tham số mở màn (Koin `parametersOf`). `recurringId == null` là tạo mới. */
data class RecurringEditorArgs(val recurringId: String?)

enum class EditorPicker { ACCOUNT, CATEGORY, INTERVAL, WEEKDAY, DAY, MONTH, REMIND, START_DATE, EFFECTIVE_DATE, END_DATE }

data class OptionUi(val id: String, val title: String, val subtitle: String? = null)

data class RecurringEditorState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val type: RecurringType = RecurringType.EXPENSE,
    /** Chuỗi đã làm sạch bằng `MoneyInput.sanitize`. */
    val amount: String = "",
    val currency: String = "",
    val accountId: String? = null,
    val accountName: String? = null,
    val accountOptions: List<OptionUi> = emptyList(),
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryOptions: List<OptionUi> = emptyList(),
    val note: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val weekday: Int = 1,
    val dayOfMonth: Int = 1,
    val month: Int = 1,
    /** Tạo mới: ngày bắt đầu (được chọn trong quá khứ, R8). */
    val startDate: LocalDate? = null,
    /** Sửa: thay đổi lịch/số tiền áp từ ngày này (≥ hôm nay, R10). */
    val effectiveFrom: LocalDate? = null,
    /** Ngày hiệu lực gợi ý (đầu kỳ kế tiếp khi kỳ này đã ghi); chọn sớm hơn thì cảnh báo. */
    val suggestedEffectiveFrom: LocalDate? = null,
    val endDate: LocalDate? = null,
    val remindDaysBefore: Int? = 1,
    val autoConfirm: Boolean = false,
    val today: LocalDate? = null,
    /** "05/12/2026", ... năm kỳ tới theo những gì đang nhập. */
    val preview: List<String> = emptyList(),
    val picker: EditorPicker? = null,
    /** Số kỳ đã qua (trong 90 ngày) sẽ được ghi nháp nếu chọn bù; khác null thì đang hỏi user (R8). */
    val backfillPrompt: Int? = null,
    val deleteConfirmVisible: Boolean = false,
    val endConfirmVisible: Boolean = false,
    val isEnded: Boolean = false,
    val isSaving: Boolean = false,
    /** Khoản bị xoá (có thể ở máy khác) trong lúc form đang mở. */
    val isDeletedElsewhere: Boolean = false,
    val nameError: UiText? = null,
    val amountError: UiText? = null,
    val error: UiText? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && amount.isNotBlank() && accountId != null && categoryId != null &&
            !isSaving && !isLoading && !isDeletedElsewhere && !isEnded

    /** Ngày hiệu lực sớm hơn gợi ý: kỳ này có thể bị ghi hai lần (một theo lịch cũ, một theo lịch mới). */
    val effectiveFromWarning: Boolean
        get() = !isNew && effectiveFrom != null && suggestedEffectiveFrom != null && effectiveFrom < suggestedEffectiveFrom
}

sealed interface RecurringEditorIntent {
    data class NameChanged(val value: String) : RecurringEditorIntent
    data class TypeChanged(val value: RecurringType) : RecurringEditorIntent
    data class AmountChanged(val value: String) : RecurringEditorIntent
    data class NoteChanged(val value: String) : RecurringEditorIntent
    data class FrequencyChanged(val value: Frequency) : RecurringEditorIntent
    data class PickerOpened(val picker: EditorPicker) : RecurringEditorIntent
    data object PickerDismissed : RecurringEditorIntent
    data class OptionSelected(val picker: EditorPicker, val id: String) : RecurringEditorIntent
    data class DateSelected(val picker: EditorPicker, val date: LocalDate) : RecurringEditorIntent
    data object EndDateCleared : RecurringEditorIntent
    data class AutoConfirmChanged(val value: Boolean) : RecurringEditorIntent
    data object SaveClicked : RecurringEditorIntent
    data class BackfillAnswered(val backfill: Boolean) : RecurringEditorIntent
    data object BackfillDismissed : RecurringEditorIntent
    data object EndClicked : RecurringEditorIntent
    data object EndConfirmed : RecurringEditorIntent
    data object EndDismissed : RecurringEditorIntent
    data object DeleteClicked : RecurringEditorIntent
    data object DeleteConfirmed : RecurringEditorIntent
    data object DeleteDismissed : RecurringEditorIntent
    data object ErrorDismissed : RecurringEditorIntent
    data object BackClicked : RecurringEditorIntent
}

sealed interface RecurringEditorEffect {
    data object Close : RecurringEditorEffect
}
