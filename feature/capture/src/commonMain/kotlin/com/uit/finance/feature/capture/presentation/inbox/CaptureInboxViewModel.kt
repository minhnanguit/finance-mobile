package com.uit.finance.feature.capture.presentation.inbox

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.capture.domain.model.Direction
import com.uit.finance.core.capture.domain.parser.BankMessageParser
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toUiText
import com.uit.finance.feature.capture.domain.usecase.CaptureInboxData
import com.uit.finance.feature.capture.domain.usecase.ConfirmDraftUseCase
import com.uit.finance.feature.capture.domain.usecase.DismissCaptureEventUseCase
import com.uit.finance.feature.capture.domain.usecase.DismissDraftUseCase
import com.uit.finance.feature.capture.domain.usecase.LinkCaptureAccountUseCase
import com.uit.finance.feature.capture.domain.usecase.ObserveCaptureInboxUseCase
import com.uit.finance.feature.capture.domain.usecase.OpenCaptureInboxUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal class CaptureInboxViewModel(
    observeInbox: ObserveCaptureInboxUseCase,
    openInbox: OpenCaptureInboxUseCase,
    private val confirmDraft: ConfirmDraftUseCase,
    private val dismissDraft: DismissDraftUseCase,
    private val dismissEvent: DismissCaptureEventUseCase,
    private val linkAccount: LinkCaptureAccountUseCase,
    private val formatter: MoneyFormatter,
) : MviViewModel<CaptureInboxState, CaptureInboxIntent, CaptureInboxEffect>(CaptureInboxState()) {

    init {
        viewModelScope.launch { openInbox() }
        observeInbox().onEach { data -> setState { render(data) } }.launchIn(viewModelScope)
    }

    private fun CaptureInboxState.render(data: CaptureInboxData): CaptureInboxState {
        val accountNames = data.accounts.associate { it.id to it.name }
        val categoryNames = data.categories.associate { it.id to it.name }
        return copy(
            isLoading = false,
            accounts = data.accounts.filter { !it.archived && it.currency == VND }.map { AccountChoice(it.id, it.name) },
            drafts = data.inbox.drafts.map { item ->
                val tx = item.transaction
                val income = tx.type == TransactionType.INCOME
                DraftRow(
                    transactionId = tx.id,
                    title = tx.payee ?: tx.note ?: tx.categoryId?.let(categoryNames::get) ?: "Giao dịch",
                    amountText = signed(tx.amount, income),
                    isIncome = income,
                    subtitle = listOfNotNull(
                        tx.categoryId?.let(categoryNames::get),
                        accountNames[tx.accountId],
                        dateLabel(tx.occurredOn),
                    ).joinToString(" · "),
                    bankName = item.bank?.displayName,
                    needsCheck = item.needsCheck,
                    possibleDuplicate = item.possibleDuplicate,
                )
            },
            needsAccount = data.inbox.needsAccount.mapNotNull { event ->
                val bank = event.bank ?: return@mapNotNull null
                val income = event.direction == Direction.IN
                NeedsAccountRow(
                    eventId = event.id,
                    bank = bank,
                    accountHint = event.accountHint,
                    title = event.note ?: if (income) "Tiền vào" else "Tiền ra",
                    amountText = event.amountMinor?.let { signed(Money(it, VND), income) }.orEmpty(),
                    subtitle = listOfNotNull(
                        bank.displayName + (event.accountHint?.let { " •••$it" } ?: ""),
                        event.occurredOn?.let(::dateLabel),
                    ).joinToString(" · "),
                )
            },
            unparsed = data.inbox.unparsed.map { event ->
                UnparsedRow(
                    eventId = event.id,
                    bankName = event.bank?.displayName,
                    preview = event.rawText?.take(PREVIEW_LENGTH) ?: "Nội dung đã được xoá sau 14 ngày",
                    manualNote = event.rawText?.let(BankMessageParser::manualEntryNote),
                )
            },
        )
    }

    override fun onIntent(intent: CaptureInboxIntent) = when (intent) {
        is CaptureInboxIntent.ConfirmClicked -> run(intent.transactionId, "Đã xác nhận giao dịch") { confirmDraft(intent.transactionId) }
        is CaptureInboxIntent.EditClicked -> sendEffect(CaptureInboxEffect.OpenTransaction(intent.transactionId))
        is CaptureInboxIntent.DismissDraftClicked -> run(intent.transactionId, "Đã bỏ qua giao dịch") { dismissDraft(intent.transactionId) }
        is CaptureInboxIntent.ChooseAccountClicked -> setState { copy(linking = intent.row) }
        is CaptureInboxIntent.AccountChosen -> {
            val row = currentState.linking
            setState { copy(linking = null) }
            if (row != null) {
                run(row.eventId, null) {
                    when (val result = linkAccount(row.bank, row.accountHint, intent.accountId)) {
                        is AppResult.Success -> {
                            setState { copy(message = UiText.Dynamic("Đã gắn ví. Tạo ${result.value} giao dịch nháp")) }
                            AppResult.Success(Unit)
                        }
                        is AppResult.Failure -> result
                    }
                }
            }
            Unit
        }
        CaptureInboxIntent.AccountPickerDismissed -> setState { copy(linking = null) }
        is CaptureInboxIntent.DismissEventClicked -> run(intent.eventId, null) { dismissEvent(intent.eventId) }
        // Không bỏ dòng này lúc mở form: user quay lại mà chưa lưu thì vẫn còn tin để nhập. Nhập xong thì bấm "Bỏ qua".
        is CaptureInboxIntent.EnterManuallyClicked ->
            sendEffect(CaptureInboxEffect.AddTransaction(currentState.unparsed.firstOrNull { it.eventId == intent.eventId }?.manualNote))
        CaptureInboxIntent.MessageShown -> setState { copy(message = null) }
    }

    private fun run(id: String, success: String?, action: suspend () -> AppResult<Unit>) {
        if (id in currentState.busyIds) return
        setState { copy(busyIds = busyIds + id) }
        viewModelScope.launch {
            val result = action()
            setState {
                copy(
                    busyIds = busyIds - id,
                    message = when (result) {
                        is AppResult.Success -> success?.let(UiText::Dynamic) ?: message
                        is AppResult.Failure -> result.error.toUiText()
                    },
                )
            }
        }
    }

    private fun signed(amount: Money, income: Boolean): String = (if (income) "+" else "-") + formatter.format(amount).text

    private fun dateLabel(date: LocalDate): String =
        "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}"

    private companion object {
        const val VND = "VND"
        const val PREVIEW_LENGTH = 160
    }
}
