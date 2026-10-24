package com.uit.finance.feature.budgets.presentation.editor

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.common.time.Clock
import com.uit.finance.core.ledger.domain.model.Category
import com.uit.finance.core.presentation.money.MoneyInput
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.ui.budget.budgetMessageFor
import com.uit.finance.core.ui.budget.toBudgetUiText
import com.uit.finance.feature.budgets.domain.model.BudgetFormData
import com.uit.finance.feature.budgets.domain.model.BudgetInput
import com.uit.finance.feature.budgets.domain.usecase.DeleteBudgetUseCase
import com.uit.finance.feature.budgets.domain.usecase.ObserveBudgetFormDataUseCase
import com.uit.finance.feature.budgets.domain.usecase.SaveBudgetUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.yearMonth

/**
 * Tạo / sửa / xoá ngân sách. Tạo mới: chọn danh mục chi cấp 1 + tiền tệ + số tiền, áp dụng từ tháng này theo lịch
 * của máy (G4). Sửa: chỉ đổi số tiền (G8).
 *
 * Danh sách danh mục tự cập nhật theo DB (sync kéo danh mục mới về); số tiền user đang gõ thì không bị đè.
 */
internal class BudgetEditorViewModel(
    private val args: BudgetEditorArgs,
    observeFormData: ObserveBudgetFormDataUseCase,
    private val saveBudget: SaveBudgetUseCase,
    private val deleteBudget: DeleteBudgetUseCase,
    private val clock: Clock,
) : MviViewModel<BudgetEditorState, BudgetEditorIntent, BudgetEditorEffect>(
    BudgetEditorState(isNew = args.budgetId == null),
) {

    private var formData: BudgetFormData? = null

    init {
        observeFormData()
            .onEach(::onFormData)
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: BudgetEditorIntent) = when (intent) {
        BudgetEditorIntent.CategoryClicked -> if (currentState.isNew) setState { copy(categoryPickerVisible = true) } else Unit
        is BudgetEditorIntent.CategorySelected -> selectCategory(intent.categoryId)
        BudgetEditorIntent.CategoryPickerDismissed -> setState { copy(categoryPickerVisible = false) }
        BudgetEditorIntent.CurrencyClicked -> if (currentState.canChooseCurrency) setState { copy(currencyPickerVisible = true) } else Unit
        is BudgetEditorIntent.CurrencySelected -> selectCurrency(intent.code)
        BudgetEditorIntent.CurrencyPickerDismissed -> setState { copy(currencyPickerVisible = false) }
        is BudgetEditorIntent.AmountChanged -> setState {
            copy(amount = MoneyInput.sanitize(intent.value, currency), amountError = null)
        }
        BudgetEditorIntent.SaveClicked -> save()
        BudgetEditorIntent.DeleteClicked -> if (!currentState.isNew) setState { copy(deleteConfirmVisible = true) } else Unit
        BudgetEditorIntent.DeleteConfirmed -> delete()
        BudgetEditorIntent.DeleteDismissed -> setState { copy(deleteConfirmVisible = false) }
        BudgetEditorIntent.ErrorDismissed -> setState { copy(error = null) }
        BudgetEditorIntent.BackClicked -> sendEffect(BudgetEditorEffect.Close)
    }

    private fun onFormData(data: BudgetFormData) {
        val first = formData == null
        formData = data
        if (args.budgetId == null) {
            setState {
                val currency = if (first) data.defaultCurrency else currency
                withOptions(data, currency).copy(isLoading = false, currencyOptions = data.currencies)
            }
            return
        }
        if (!first) return // form sửa chỉ điền một lần; user đang gõ thì không đè
        val budget = data.budgets.firstOrNull { it.id == args.budgetId }
        if (budget == null) {
            sendEffect(BudgetEditorEffect.Close) // đã bị xoá (có thể ở máy khác)
            return
        }
        setState {
            copy(
                isLoading = false,
                categoryId = budget.categoryId,
                categoryName = data.allCategories.firstOrNull { it.id == budget.categoryId }?.name,
                currency = budget.currency,
                currencyOptions = listOf(budget.currency),
                amount = MoneyInput.fromMinor(budget.amount.amountMinor, budget.currency),
            )
        }
    }

    /** Danh mục chọn được ở [currency]; danh mục đang chọn mà đã có ngân sách ở tiền tệ đó thì bỏ chọn. */
    private fun BudgetEditorState.withOptions(data: BudgetFormData, currency: String): BudgetEditorState {
        val options = data.availableCategories(currency)
        val keep = options.firstOrNull { it.id == categoryId }
        return copy(
            currency = currency,
            categoryOptions = options.map { it.toOption() },
            categoryId = keep?.id,
            categoryName = keep?.name,
        )
    }

    private fun selectCategory(categoryId: String) {
        val option = currentState.categoryOptions.firstOrNull { it.id == categoryId } ?: return
        setState { copy(categoryId = option.id, categoryName = option.name, categoryPickerVisible = false, categoryError = null) }
    }

    private fun selectCurrency(code: String) {
        val data = formData ?: return
        setState {
            withOptions(data, code).copy(
                amount = MoneyInput.convert(amount, code),
                currencyPickerVisible = false,
                amountError = null,
            )
        }
    }

    private fun save() {
        val state = currentState
        val categoryId = state.categoryId
        if (!state.canSave || categoryId == null) return
        setState { copy(isSaving = true, error = null) }
        val input = BudgetInput(
            id = args.budgetId,
            categoryId = categoryId,
            currency = state.currency,
            amountMinor = MoneyInput.toMinor(state.amount, state.currency) ?: 0L,
            startMonth = clock.today().yearMonth,
        )
        viewModelScope.launch {
            saveBudget(input).fold(
                onSuccess = { sendEffect(BudgetEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false).withError(error) } },
            )
        }
    }

    private fun delete() {
        val budgetId = args.budgetId ?: return
        setState { copy(deleteConfirmVisible = false, isSaving = true) }
        viewModelScope.launch {
            deleteBudget(budgetId).fold(
                onSuccess = { sendEffect(BudgetEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false, error = error.toBudgetUiText()) } },
            )
        }
    }

    /**
     * Lỗi theo trường hiện dưới đúng ô (kể cả `budget.duplicate`, gắn với danh mục); lỗi còn lại (quá giới hạn,
     * lưu trữ...) hiện ở banner.
     */
    private fun BudgetEditorState.withError(error: AppError): BudgetEditorState {
        val fields = (error as? AppError.Validation)?.fieldErrors.orEmpty()
        val amountError = fields.budgetMessageFor("amountMinor")
        val categoryError = fields.budgetMessageFor("categoryId")
        return copy(
            amountError = amountError,
            categoryError = categoryError,
            error = if (amountError != null || categoryError != null) null else error.toBudgetUiText(),
        )
    }

    private fun Category.toOption() = CategoryOptionUi(id = id, name = name, icon = icon, color = color)
}
