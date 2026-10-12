package com.uit.finance.feature.transactions.presentation.editor

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.ledger.domain.model.Account
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.domain.model.Transaction
import com.uit.finance.core.ledger.domain.model.TransactionType
import com.uit.finance.core.ledger.domain.validation.LedgerLimits
import com.uit.finance.core.presentation.money.MoneyInput
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.presentation.text.UiText
import com.uit.finance.core.presentation.text.toLongLabel
import com.uit.finance.core.presentation.text.toRelativeLabel
import com.uit.finance.core.ui.ledger.messageFor
import com.uit.finance.core.ui.ledger.toLedgerUiText
import com.uit.finance.feature.transactions.domain.model.TransactionErrorCode
import com.uit.finance.feature.transactions.domain.model.TransactionFormData
import com.uit.finance.feature.transactions.domain.model.TransactionInput
import com.uit.finance.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionFormDataUseCase
import com.uit.finance.feature.transactions.domain.usecase.ObserveTransactionUseCase
import com.uit.finance.feature.transactions.domain.usecase.SaveTransactionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Form thêm/sửa giao dịch. Thêm mới mặc định: Chi, ví đầu tiên, hôm nay; con trỏ ở ô số tiền. Nên luồng nhanh nhất
 * là gõ số tiền → chạm danh mục → Lưu (≤ 3 lần chạm, Phase 5).
 */
internal class TransactionEditorViewModel(
    private val args: TransactionEditorArgs,
    observeFormData: ObserveTransactionFormDataUseCase,
    private val observeTransaction: ObserveTransactionUseCase,
    private val saveTransaction: SaveTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val clock: Clock,
) : MviViewModel<TransactionEditorState, TransactionEditorIntent, TransactionEditorEffect>(
    TransactionEditorState(isNew = args.transactionId == null),
) {
    private var formData = TransactionFormData(emptyList(), emptyList())

    /** Ví/danh mục gốc của giao dịch đang sửa: vẫn hiện dù đã lưu trữ (luật archive chỉ chặn khi gắn mới). */
    private var original: Transaction? = null

    init {
        val today = clock.today()
        setState {
            copy(
                occurredOn = today,
                dateLabel = today.label(today),
                dateRange = LedgerLimits.EARLIEST_DATE..today.plus(LedgerLimits.MAX_AHEAD),
            )
        }
        viewModelScope.launch {
            args.transactionId?.let { id ->
                val transaction = observeTransaction(id).first()
                if (transaction == null) {
                    sendEffect(TransactionEditorEffect.Close)
                    return@launch
                }
                original = transaction
                setState { fill(transaction) }
            }
            observeFormData()
                .onEach { data ->
                    formData = data
                    setState { withOptions().copy(isLoading = false) }
                }
                .launchIn(viewModelScope)
        }
    }

    override fun onIntent(intent: TransactionEditorIntent) = when (intent) {
        is TransactionEditorIntent.TypeChanged -> setState { copy(type = intent.value, categoryError = null).withOptions() }
        is TransactionEditorIntent.AmountChanged -> setState {
            copy(amount = MoneyInput.sanitize(intent.value, currency), amountError = null)
        }
        is TransactionEditorIntent.FeeChanged -> setState { copy(fee = MoneyInput.sanitize(intent.value, currency), feeError = null) }
        is TransactionEditorIntent.CategorySelected -> setState { copy(categoryId = intent.categoryId, categoryError = null) }
        is TransactionEditorIntent.PickerOpened -> setState { copy(picker = intent.picker) }
        TransactionEditorIntent.PickerDismissed -> setState { copy(picker = null) }
        is TransactionEditorIntent.AccountSelected -> setState {
            copy(accountId = intent.accountId, picker = null, accountError = null).withOptions()
        }
        is TransactionEditorIntent.CounterAccountSelected -> setState {
            copy(counterAccountId = intent.accountId, picker = null, counterAccountError = null)
        }
        is TransactionEditorIntent.DateSelected -> setState {
            copy(occurredOn = intent.date, dateLabel = intent.date.label(clock.today()), picker = null, dateError = null)
        }
        is TransactionEditorIntent.PayeeChanged -> setState { copy(payee = intent.value, payeeError = null) }
        is TransactionEditorIntent.NoteChanged -> setState { copy(note = intent.value, noteError = null) }
        TransactionEditorIntent.SaveClicked -> save()
        TransactionEditorIntent.DeleteClicked -> setState { copy(confirmDelete = true) }
        TransactionEditorIntent.DeleteConfirmed -> delete()
        TransactionEditorIntent.DeleteDismissed -> setState { copy(confirmDelete = false) }
        TransactionEditorIntent.ErrorDismissed -> setState { copy(error = null) }
        TransactionEditorIntent.CreateAccountClicked -> sendEffect(TransactionEditorEffect.OpenCreateAccount)
        TransactionEditorIntent.ManageCategoriesClicked -> sendEffect(
            TransactionEditorEffect.OpenCategories(if (currentState.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE),
        )
        TransactionEditorIntent.BackClicked -> sendEffect(TransactionEditorEffect.Close)
    }

    private fun TransactionEditorState.fill(transaction: Transaction) = copy(
        type = transaction.type,
        amount = MoneyInput.fromMinor(transaction.amount.amountMinor, transaction.amount.currency),
        currency = transaction.amount.currency,
        accountId = transaction.accountId,
        counterAccountId = transaction.counterAccountId,
        categoryId = transaction.categoryId,
        occurredOn = transaction.occurredOn,
        dateLabel = transaction.occurredOn.label(clock.today()),
        payee = transaction.payee.orEmpty(),
        note = transaction.note.orEmpty(),
    )

    /**
     * Tính lại lựa chọn theo dữ liệu mới nhất + lựa chọn hiện tại: ví mặc định (ví đầu tiên), tiền tệ theo ví (đổi ví
     * khác tiền tệ thì cắt lại phần lẻ), ví nhận hợp lệ, danh mục đúng loại. Lựa chọn không còn hợp lệ thì bỏ.
     */
    private fun TransactionEditorState.withOptions(): TransactionEditorState {
        val keep = original
        val usable = formData.accounts.filter { !it.archived || it.id == keep?.accountId || it.id == keep?.counterAccountId }
        val account = usable.firstOrNull { it.id == accountId } ?: usable.firstOrNull { !it.archived }
        val newCurrency = account?.currency ?: currency
        val counters = usable.filter { it.id != account?.id && it.currency == newCurrency && (!it.archived || it.id == keep?.counterAccountId) }
        val kind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val categories = formData.categories.filter { it.kind == kind && (!it.archived || it.id == keep?.categoryId) }
        return copy(
            accounts = usable.filter { !it.archived || it.id == keep?.accountId }.map { it.toOption() },
            counterAccounts = counters.map { it.toOption() },
            categories = categories.map { CategoryOptionUi(it.id, it.name, it.kind, it.icon, it.color) },
            accountId = account?.id,
            currency = newCurrency,
            amount = if (newCurrency != currency) MoneyInput.convert(amount, newCurrency) else amount,
            fee = if (newCurrency != currency) MoneyInput.convert(fee, newCurrency) else fee,
            counterAccountId = counterAccountId?.takeIf { id -> counters.any { it.id == id } },
            categoryId = if (type == TransactionType.TRANSFER) null else categoryId?.takeIf { id -> categories.any { it.id == id } },
        )
    }

    private fun save() {
        val state = currentState
        if (!state.canSave) return
        setState { copy(isSaving = true, error = null) }
        val input = TransactionInput(
            id = args.transactionId,
            type = state.type,
            accountId = requireNotNull(state.accountId),
            counterAccountId = state.counterAccountId,
            categoryId = state.categoryId,
            amountMinor = MoneyInput.toMinor(state.amount, state.currency) ?: 0L,
            feeMinor = if (state.isNew && state.isTransfer) MoneyInput.toMinor(state.fee, state.currency) ?: 0L else 0L,
            occurredOn = state.occurredOn,
            payee = state.payee,
            note = state.note,
        )
        viewModelScope.launch {
            saveTransaction(input).fold(
                onSuccess = { sendEffect(TransactionEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false).withError(error) } },
            )
        }
    }

    private fun delete() {
        val id = args.transactionId ?: return
        setState { copy(confirmDelete = false, isSaving = true) }
        viewModelScope.launch {
            deleteTransaction(id).fold(
                onSuccess = { sendEffect(TransactionEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false, error = error.toLedgerUiText()) } },
            )
        }
    }

    /** Lỗi theo trường hiện dưới đúng ô; còn lại hiện ở banner trên cùng. */
    private fun TransactionEditorState.withError(error: AppError): TransactionEditorState {
        val fields = (error as? AppError.Validation)?.fieldErrors.orEmpty()
        val updated = copy(
            amountError = fields.messageFor("amountMinor"),
            feeError = fields.feeMessage(),
            categoryError = fields.messageFor("categoryId"),
            accountError = fields.messageFor("accountId"),
            counterAccountError = fields.messageFor("counterAccountId"),
            dateError = fields.messageFor("occurredOn"),
            payeeError = fields.messageFor("payee"),
            noteError = fields.messageFor("note"),
        )
        val handled = listOf(
            updated.amountError, updated.feeError, updated.categoryError, updated.accountError,
            updated.counterAccountError, updated.dateError, updated.payeeError, updated.noteError,
        ).any { it != null }
        return updated.copy(error = if (handled) null else error.toLedgerUiText())
    }

    private fun List<FieldError>.feeMessage(): UiText? {
        val fee = firstOrNull { it.field == "feeMinor" } ?: return null
        return if (fee.message == TransactionErrorCode.FEE_CATEGORY_MISSING) {
            UiText.Key(
                "transactions.fee_category_missing",
                "Chưa có danh mục \"Phí giao dịch\". Hãy đồng bộ lần đầu (cần mạng) rồi thử lại, hoặc bỏ trống phí.",
            )
        } else {
            UiText.Key("transactions.fee_invalid", "Phí phải lớn hơn 0 và không quá 1 triệu tỷ.")
        }
    }

    private fun Account.toOption() = AccountOptionUi(id, name, type, currency)

    /** "Hôm nay · Thứ Bảy, 10 tháng 10"; ngày khác năm thì thêm năm. */
    private fun LocalDate.label(today: LocalDate): String = when (this) {
        today, today.minus(1, DateTimeUnit.DAY) -> "${toRelativeLabel(today)} · ${toLongLabel()}"
        else -> if (year == today.year) toLongLabel() else "${toLongLabel()}, $year"
    }
}
