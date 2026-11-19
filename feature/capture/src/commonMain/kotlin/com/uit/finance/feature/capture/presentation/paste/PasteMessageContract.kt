package com.uit.finance.feature.capture.presentation.paste

import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.capture.domain.repository.PastePreview
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.feature.capture.presentation.inbox.AccountChoice

data class CategoryChoice(val id: String, val name: String, val income: Boolean)

enum class PastePicker { BANK, ACCOUNT, CATEGORY }

data class PasteMessageState(
    val text: String = "",
    val bank: BankId? = null,
    val banks: List<BankId> = emptyList(),
    val accounts: List<AccountChoice> = emptyList(),
    val categories: List<CategoryChoice> = emptyList(),
    val isReading: Boolean = false,
    /** Có sau khi đọc được tin: hiện form điền sẵn. */
    val preview: PastePreview? = null,
    val isIncome: Boolean = false,
    val amountInput: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val dateLabel: String = "",
    val note: String = "",
    val rememberAccount: Boolean = true,
    val isSaving: Boolean = false,
    val picker: PastePicker? = null,
    val error: UiText? = null,
    /** Tin không đọc được: gợi ý tự nhập. */
    val offerManualEntry: Boolean = false,
) {
    val accountName: String get() = accounts.firstOrNull { it.id == accountId }?.name ?: "Chọn ví"
    val categoryName: String get() = categories.firstOrNull { it.id == categoryId }?.name ?: "Chọn danh mục"
    val bankName: String get() = bank?.displayName ?: "Không rõ"
    val accountHint: String? get() = preview?.event?.accountHint
    val canRemember: Boolean get() = bank != null && accountHint != null
    val canRead: Boolean get() = text.isNotBlank() && !isReading
    val canSave: Boolean get() = preview != null && accountId != null && categoryId != null &&
        (amountInput.toLongOrNull() ?: 0) > 0 && !isSaving
}

sealed interface PasteMessageIntent {
    data class TextChanged(val value: String) : PasteMessageIntent
    data object ReadClicked : PasteMessageIntent
    data class PickerOpened(val picker: PastePicker) : PasteMessageIntent
    data object PickerDismissed : PasteMessageIntent
    data class BankPicked(val bank: BankId?) : PasteMessageIntent
    data class AccountPicked(val accountId: String) : PasteMessageIntent
    data class CategoryPicked(val categoryId: String) : PasteMessageIntent
    data class AmountChanged(val value: String) : PasteMessageIntent
    data class NoteChanged(val value: String) : PasteMessageIntent
    data class RememberToggled(val value: Boolean) : PasteMessageIntent
    data object SaveClicked : PasteMessageIntent
    data object ManualEntryClicked : PasteMessageIntent
}

sealed interface PasteMessageEffect {
    data object Saved : PasteMessageEffect
    data object AddTransaction : PasteMessageEffect
}
