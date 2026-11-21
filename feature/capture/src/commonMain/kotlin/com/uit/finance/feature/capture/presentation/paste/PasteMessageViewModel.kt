package com.uit.finance.feature.capture.presentation.paste

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.capture.domain.model.Direction
import com.uit.finance.core.capture.domain.model.IngestOutcome
import com.uit.finance.core.capture.domain.repository.PasteParseResult
import com.uit.finance.core.capture.domain.repository.PasteSubmission
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toUiText
import com.uit.finance.feature.capture.domain.usecase.ObservePasteFormDataUseCase
import com.uit.finance.feature.capture.domain.usecase.ParsePastedMessageUseCase
import com.uit.finance.feature.capture.domain.usecase.SavePastedMessageUseCase
import com.uit.finance.feature.capture.presentation.inbox.AccountChoice
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Dán tin nhắn ngân hàng (plan I2, B18): user tự dán bằng nhấn giữ → Dán (không đọc clipboard bằng code), đọc ra form
 * điền sẵn, bấm Lưu thì ghi **đã xác nhận**. Dán lại đúng tin đó thì báo "đã ghi".
 */
internal class PasteMessageViewModel(
    observeFormData: ObservePasteFormDataUseCase,
    private val parse: ParsePastedMessageUseCase,
    private val save: SavePastedMessageUseCase,
) : MviViewModel<PasteMessageState, PasteMessageIntent, PasteMessageEffect>(PasteMessageState()) {

    init {
        observeFormData().onEach { data ->
            setState {
                copy(
                    banks = data.banks,
                    accounts = data.accounts.map { AccountChoice(it.id, it.name) },
                    categories = data.categories.map { CategoryChoice(it.id, it.name, it.kind == CategoryKind.INCOME) },
                )
            }
        }.launchIn(viewModelScope)
    }

    override fun onIntent(intent: PasteMessageIntent) = when (intent) {
        is PasteMessageIntent.TextChanged -> setState { copy(text = intent.value, error = null, offerManualEntry = false) }
        PasteMessageIntent.ReadClicked -> read()
        is PasteMessageIntent.PickerOpened -> setState { copy(picker = intent.picker) }
        PasteMessageIntent.PickerDismissed -> setState { copy(picker = null) }
        is PasteMessageIntent.BankPicked -> setState { copy(bank = intent.bank, picker = null) }
        is PasteMessageIntent.AccountPicked -> setState { copy(accountId = intent.accountId, picker = null) }
        is PasteMessageIntent.CategoryPicked -> setState { copy(categoryId = intent.categoryId, picker = null) }
        is PasteMessageIntent.AmountChanged -> setState { copy(amountInput = intent.value.filter(Char::isDigit).take(MAX_DIGITS)) }
        is PasteMessageIntent.NoteChanged -> setState { copy(note = intent.value.take(MAX_NOTE)) }
        is PasteMessageIntent.RememberToggled -> setState { copy(rememberAccount = intent.value) }
        PasteMessageIntent.SaveClicked -> submit()
        PasteMessageIntent.ManualEntryClicked -> sendEffect(PasteMessageEffect.AddTransaction)
    }

    private fun read() {
        val state = currentState
        if (!state.canRead) return
        setState { copy(isReading = true, error = null, offerManualEntry = false) }
        viewModelScope.launch {
            when (val result = parse(state.text, state.bank)) {
                is PasteParseResult.Ready -> {
                    val preview = result.preview
                    val income = preview.event.direction == Direction.IN
                    val date = preview.occurredOn
                    setState {
                        copy(
                            isReading = false,
                            preview = preview,
                            isIncome = income,
                            amountInput = preview.event.amountMinor.toString(),
                            accountId = preview.suggestedAccountId ?: accountId,
                            // Repository đã chọn danh mục đúng loại (mặc định của user, hoặc danh mục gốc đầu tiên).
                            categoryId = preview.suggestedCategoryId,
                            dateLabel = "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}",
                            note = preview.event.description.orEmpty(),
                        )
                    }
                }
                PasteParseResult.Otp -> setState {
                    copy(isReading = false, preview = null, error = UiText.Dynamic("Đây là tin mã OTP: không lưu. Đừng chia sẻ mã này cho ai."))
                }
                PasteParseResult.NotReadable -> setState {
                    copy(
                        isReading = false,
                        preview = null,
                        offerManualEntry = true,
                        error = UiText.Dynamic("Không đọc được số tiền và chiều giao dịch trong tin này."),
                    )
                }
            }
        }
    }

    private fun submit() {
        val state = currentState
        val preview = state.preview ?: return
        val accountId = state.accountId ?: return
        val categoryId = state.categoryId ?: return
        if (!state.canSave) return
        setState { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val submission = PasteSubmission(
                preview = preview,
                bank = state.bank,
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = state.amountInput.toLong(),
                occurredOn = preview.occurredOn,
                note = state.note,
                rememberAccount = state.rememberAccount && state.canRemember,
            )
            when (val result = save(submission)) {
                is AppResult.Success -> when (result.value) {
                    IngestOutcome.ALREADY_EXISTS -> setState { copy(isSaving = false, error = UiText.Dynamic("Giao dịch này đã được ghi rồi.")) }
                    IngestOutcome.REJECTED -> setState {
                        copy(isSaving = false, error = UiText.Dynamic("Ví hoặc danh mục không dùng được (đã lưu trữ hoặc khác loại). Hãy chọn lại."))
                    }
                    else -> {
                        setState { copy(isSaving = false) }
                        sendEffect(PasteMessageEffect.Saved)
                    }
                }
                is AppResult.Failure -> setState { copy(isSaving = false, error = result.error.toUiText()) }
            }
        }
    }

    private companion object {
        const val MAX_DIGITS = 15
        const val MAX_NOTE = 500
    }
}
