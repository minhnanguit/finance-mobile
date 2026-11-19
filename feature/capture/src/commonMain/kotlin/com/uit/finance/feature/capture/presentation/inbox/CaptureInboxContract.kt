package com.uit.finance.feature.capture.presentation.inbox

import com.uit.finance.core.capture.domain.model.BankId
import com.uit.finance.core.presentation.text.UiText

/** Một nháp đã format sẵn để hiển thị. */
data class DraftRow(
    val transactionId: String,
    val title: String,
    val amountText: String,
    val isIncome: Boolean,
    val subtitle: String,
    val bankName: String?,
    val needsCheck: Boolean,
    val possibleDuplicate: Boolean,
)

/** Dòng "Cần chọn ví". */
data class NeedsAccountRow(
    val eventId: String,
    val bank: BankId,
    val accountHint: String?,
    val title: String,
    val amountText: String,
    val subtitle: String,
)

/** Dòng "Không đọc được". */
data class UnparsedRow(
    val eventId: String,
    val bankName: String?,
    val preview: String,
)

data class AccountChoice(val id: String, val name: String)

data class CaptureInboxState(
    val isLoading: Boolean = true,
    val drafts: List<DraftRow> = emptyList(),
    val needsAccount: List<NeedsAccountRow> = emptyList(),
    val unparsed: List<UnparsedRow> = emptyList(),
    val accounts: List<AccountChoice> = emptyList(),
    /** Đang chọn ví cho dòng nào. */
    val linking: NeedsAccountRow? = null,
    val busyIds: Set<String> = emptySet(),
    val message: UiText? = null,
) {
    val isEmpty: Boolean get() = !isLoading && drafts.isEmpty() && needsAccount.isEmpty() && unparsed.isEmpty()
}

sealed interface CaptureInboxIntent {
    data class ConfirmClicked(val transactionId: String) : CaptureInboxIntent
    data class EditClicked(val transactionId: String) : CaptureInboxIntent
    data class DismissDraftClicked(val transactionId: String) : CaptureInboxIntent
    data class ChooseAccountClicked(val row: NeedsAccountRow) : CaptureInboxIntent
    data class AccountChosen(val accountId: String) : CaptureInboxIntent
    data object AccountPickerDismissed : CaptureInboxIntent
    data class DismissEventClicked(val eventId: String) : CaptureInboxIntent
    data class EnterManuallyClicked(val eventId: String) : CaptureInboxIntent
    data object MessageShown : CaptureInboxIntent
}

sealed interface CaptureInboxEffect {
    data class OpenTransaction(val transactionId: String) : CaptureInboxEffect
    data object AddTransaction : CaptureInboxEffect
}
