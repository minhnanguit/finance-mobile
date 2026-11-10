package com.uit.finance.feature.recurring.presentation.editor

import androidx.lifecycle.viewModelScope
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.common.result.map
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.common.result.fold
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.presentation.money.MoneyInput
import com.uit.finance.core.presentation.mvi.MviViewModel
import com.uit.finance.core.recurring.domain.engine.RecurrenceEngine
import com.uit.finance.core.recurring.domain.model.Frequency
import com.uit.finance.core.recurring.domain.model.RecurrenceRevision
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.domain.model.Recurring
import com.uit.finance.core.recurring.domain.model.RecurringDraft
import com.uit.finance.core.recurring.domain.model.RecurringErrorCode
import com.uit.finance.core.recurring.domain.model.RecurringType
import com.uit.finance.core.recurring.domain.model.RevisionChange
import com.uit.finance.core.recurring.domain.validation.RecurringLimits
import com.uit.finance.feature.recurring.domain.model.RecurringFormData
import com.uit.finance.feature.recurring.domain.usecase.CreateRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.DeleteRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.EndRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.ObserveRecurringFormDataUseCase
import com.uit.finance.feature.recurring.domain.usecase.RequestReminderPermissionUseCase
import com.uit.finance.feature.recurring.domain.usecase.UpdateRecurringUseCase
import com.uit.finance.feature.recurring.presentation.shortLabel
import com.uit.finance.feature.recurring.presentation.toRecurringUiText
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/**
 * Tạo / sửa khoản định kỳ (plan mục 2.2).
 *
 * - Tạo: bắt đầu trong quá khứ thì hỏi ghi bù các kỳ trong 90 ngày thành nháp hay chỉ từ hôm nay (R8).
 * - Sửa: tên, nhắc áp ngay; lịch, số tiền, ví, danh mục, ghi chú, tự xác nhận áp **từ ngày hiệu lực** (≥ hôm nay, R10).
 *   Đổi lịch mà kỳ này đã ghi theo lịch cũ thì gợi ý hiệu lực từ đầu kỳ sau, để không ghi hai lần.
 * - Dữ liệu tự cập nhật theo DB (sync, xung đột revision lấy bản server); thứ user đang gõ không bị đè.
 */
internal class RecurringEditorViewModel(
    private val args: RecurringEditorArgs,
    observeFormData: ObserveRecurringFormDataUseCase,
    private val createRecurring: CreateRecurringUseCase,
    private val updateRecurring: UpdateRecurringUseCase,
    private val endRecurring: EndRecurringUseCase,
    private val deleteRecurring: DeleteRecurringUseCase,
    private val requestReminderPermission: RequestReminderPermissionUseCase,
) : MviViewModel<RecurringEditorState, RecurringEditorIntent, RecurringEditorEffect>(
    RecurringEditorState(isNew = args.recurringId == null),
) {

    private var formData: RecurringFormData? = null

    /** Bản đang sửa mới nhất trên máy (đổi khi sync về, kể cả sau `revision_conflict`). */
    private var current: Recurring? = null

    init {
        observeFormData(args.recurringId)
            .onEach(::onFormData)
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: RecurringEditorIntent) = when (intent) {
        is RecurringEditorIntent.NameChanged -> setState { copy(name = intent.value, nameError = null) }
        is RecurringEditorIntent.TypeChanged -> if (currentState.isNew) changeType(intent.value) else Unit
        is RecurringEditorIntent.AmountChanged -> setState { copy(amount = MoneyInput.sanitize(intent.value, currency), amountError = null) }
        is RecurringEditorIntent.NoteChanged -> setState { copy(note = intent.value) }
        is RecurringEditorIntent.FrequencyChanged -> updateSchedule { copy(frequency = intent.value) }
        is RecurringEditorIntent.PickerOpened -> setState { copy(picker = intent.picker) }
        RecurringEditorIntent.PickerDismissed -> setState { copy(picker = null) }
        is RecurringEditorIntent.OptionSelected -> select(intent.picker, intent.id)
        is RecurringEditorIntent.DateSelected -> selectDate(intent.picker, intent.date)
        RecurringEditorIntent.EndDateCleared -> refreshed { copy(endDate = null, picker = null) }
        is RecurringEditorIntent.AutoConfirmChanged -> setState { copy(autoConfirm = intent.value) }
        RecurringEditorIntent.SaveClicked -> save(backfill = null)
        is RecurringEditorIntent.BackfillAnswered -> {
            setState { copy(backfillPrompt = null) }
            save(backfill = intent.backfill)
        }
        RecurringEditorIntent.BackfillDismissed -> setState { copy(backfillPrompt = null) }
        RecurringEditorIntent.EndClicked -> if (!currentState.isNew && !currentState.isEnded) setState { copy(endConfirmVisible = true) } else Unit
        RecurringEditorIntent.EndConfirmed -> end()
        RecurringEditorIntent.EndDismissed -> setState { copy(endConfirmVisible = false) }
        RecurringEditorIntent.DeleteClicked -> if (!currentState.isNew) setState { copy(deleteConfirmVisible = true) } else Unit
        RecurringEditorIntent.DeleteConfirmed -> delete()
        RecurringEditorIntent.DeleteDismissed -> setState { copy(deleteConfirmVisible = false) }
        RecurringEditorIntent.ErrorDismissed -> setState { copy(error = null) }
        RecurringEditorIntent.BackClicked -> sendEffect(RecurringEditorEffect.Close)
    }

    // ---- Dữ liệu từ DB ----

    private fun onFormData(data: RecurringFormData) {
        val first = formData == null
        formData = data
        val today = data.today
        if (args.recurringId == null) {
            if (first) {
                val account = data.accounts.firstOrNull()
                setState {
                    copy(
                        isLoading = false,
                        today = today,
                        accountId = account?.id,
                        accountName = account?.name,
                        currency = account?.currency.orEmpty(),
                        startDate = today,
                        dayOfMonth = today.day,
                        weekday = today.dayOfWeek.ordinal + 1,
                        month = today.month.ordinal + 1,
                    ).withOptions(data).withDefaultCategory(data).withPreview()
                }
            } else {
                setState { copy(today = today).withOptions(data).withPreview() }
            }
            return
        }
        val recurring = data.recurring
        if (recurring == null) {
            if (first) {
                sendEffect(RecurringEditorEffect.Close)
            } else {
                setState {
                    copy(
                        isDeletedElsewhere = true,
                        deleteConfirmVisible = false,
                        endConfirmVisible = false,
                        error = FieldError("recurring", RecurringErrorCode.DELETED).toRecurringUiText(),
                    )
                }
            }
            return
        }
        current = recurring
        if (!first) {
            setState { copy(today = today, isEnded = recurring.isEnded(today)).withOptions(data).withSuggestion().withPreview() }
            return
        }
        val revision = recurring.currentRevision(today)
        val accountName = data.accounts.firstOrNull { it.id == revision.accountId }?.name
        val categoryName = data.categories.firstOrNull { it.id == revision.categoryId }?.name
        setState {
            copy(
                isLoading = false,
                today = today,
                name = recurring.name,
                type = recurring.type,
                currency = recurring.currency,
                amount = MoneyInput.fromMinor(revision.amountMinor, recurring.currency),
                accountId = revision.accountId,
                accountName = accountName ?: "Ví không còn dùng được",
                categoryId = revision.categoryId,
                categoryName = categoryName ?: "Danh mục không còn dùng được",
                note = revision.note.orEmpty(),
                frequency = revision.rule.frequency,
                interval = revision.rule.interval,
                weekday = revision.rule.weekday ?: (today.dayOfWeek.ordinal + 1),
                dayOfMonth = revision.rule.dayOfMonth ?: today.day,
                month = revision.rule.month ?: (today.month.ordinal + 1),
                endDate = recurring.generationEndDate,
                remindDaysBefore = recurring.remindDaysBefore,
                autoConfirm = revision.autoConfirm,
                effectiveFrom = today,
                isEnded = recurring.isEnded(today),
            ).withOptions(data).withSuggestion().withPreview()
        }
    }

    private fun RecurringEditorState.withOptions(data: RecurringFormData): RecurringEditorState {
        val kind = if (type == RecurringType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val accounts = data.accounts.filter { isNew || it.currency == currency }
        return copy(
            accountOptions = accounts.map { OptionUi(it.id, it.name, it.currency) },
            categoryOptions = data.categories.filter { it.kind == kind }.map { OptionUi(it.id, it.name) },
        )
    }

    private fun RecurringEditorState.withDefaultCategory(data: RecurringFormData): RecurringEditorState {
        val kind = if (type == RecurringType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val category = data.categories.firstOrNull { it.kind == kind && it.parentId == null }
        return copy(categoryId = category?.id, categoryName = category?.name)
    }

    // ---- Chỉnh form ----

    private fun changeType(type: RecurringType) {
        val data = formData ?: return
        setState { copy(type = type).withOptions(data).withDefaultCategory(data) }
    }

    private fun updateSchedule(change: RecurringEditorState.() -> RecurringEditorState) =
        setState { change().withSuggestion(resetEffective = true).withPreview() }

    private fun refreshed(change: RecurringEditorState.() -> RecurringEditorState) = setState { change().withPreview() }

    private fun select(picker: EditorPicker, id: String) {
        when (picker) {
            EditorPicker.ACCOUNT -> {
                val account = formData?.accounts?.firstOrNull { it.id == id } ?: return
                setState {
                    copy(
                        accountId = account.id,
                        accountName = account.name,
                        currency = account.currency,
                        amount = MoneyInput.convert(amount, account.currency),
                        picker = null,
                    )
                }
            }
            EditorPicker.CATEGORY -> {
                val option = currentState.categoryOptions.firstOrNull { it.id == id } ?: return
                setState { copy(categoryId = option.id, categoryName = option.title, picker = null) }
            }
            EditorPicker.INTERVAL -> updateSchedule { copy(interval = id.toInt(), picker = null) }
            EditorPicker.WEEKDAY -> updateSchedule { copy(weekday = id.toInt(), picker = null) }
            EditorPicker.DAY -> updateSchedule { copy(dayOfMonth = id.toInt(), picker = null) }
            EditorPicker.MONTH -> updateSchedule { copy(month = id.toInt(), picker = null) }
            EditorPicker.REMIND -> setState { copy(remindDaysBefore = id.toIntOrNull(), picker = null) }
            else -> Unit
        }
    }

    private fun selectDate(picker: EditorPicker, date: LocalDate) = when (picker) {
        EditorPicker.START_DATE -> refreshed { copy(startDate = date, picker = null) }
        EditorPicker.EFFECTIVE_DATE -> refreshed { copy(effectiveFrom = date, picker = null) }
        EditorPicker.END_DATE -> refreshed { copy(endDate = date, picker = null) }
        else -> Unit
    }

    // ---- Lịch: xem trước, gợi ý ngày hiệu lực ----

    private fun RecurringEditorState.rule(): RecurrenceRule = when (frequency) {
        Frequency.WEEKLY -> RecurrenceRule.weekly(weekday, interval)
        Frequency.MONTHLY -> RecurrenceRule.monthly(dayOfMonth, interval)
        Frequency.YEARLY -> RecurrenceRule.yearly(month, dayOfMonth, interval)
    }

    /** Khoản giả định theo những gì đang nhập, chỉ để tính kỳ. */
    private fun RecurringEditorState.hypothetical(): Recurring? {
        val today = today ?: return null
        val rule = rule()
        val existing = current
        val revisions = if (existing == null) {
            val start = startDate ?: today
            listOf(revision(start, start, rule))
        } else {
            val from = effectiveFrom ?: today
            val kept = existing.revisions.filter { it.effectiveFrom < from }
            val base = kept.lastOrNull() ?: existing.revisions.first()
            val anchor = if (base.rule.sameCadenceAs(rule)) base.anchorDate else from
            kept + revision(from, minOf(anchor, from), rule)
        }
        return Recurring(
            id = existing?.id ?: "preview",
            name = name,
            type = type,
            currency = currency,
            generationNotBefore = existing?.generationNotBefore ?: (startDate ?: today),
            generationEndDate = endDate,
            remindDaysBefore = remindDaysBefore,
            revision = 0,
            revisions = revisions,
        )
    }

    private fun RecurringEditorState.revision(from: LocalDate, anchor: LocalDate, rule: RecurrenceRule) =
        RecurrenceRevision("preview", from, anchor, accountId.orEmpty(), categoryId.orEmpty(), 1, null, rule, false)

    private fun RecurringEditorState.withPreview(): RecurringEditorState {
        val today = today ?: return this
        val recurring = hypothetical() ?: return this
        val from = maxOf(today, startDate ?: today)
        val dates = RecurrenceEngine.occurrences(recurring, from, from.plus(DatePeriod(days = PREVIEW_HORIZON_DAYS)))
            .take(PREVIEW_COUNT).map { it.date.shortLabel() }
        return copy(preview = dates)
    }

    /**
     * Đổi lịch mà kỳ hiện tại (tuần/tháng/năm của lịch mới) đã có kỳ theo lịch cũ thì gợi ý hiệu lực từ đầu kỳ sau,
     * để không ghi hai lần trong một kỳ. Lịch không đổi thì hiệu lực từ hôm nay.
     */
    private fun RecurringEditorState.withSuggestion(resetEffective: Boolean = false): RecurringEditorState {
        val existing = current ?: return this
        val today = today ?: return this
        val rule = rule()
        val sameRule = existing.currentRevision(today).rule == rule
        val lastOld = RecurrenceEngine.occurrences(existing, today.minus(DatePeriod(days = LOOKBACK_DAYS)), today).lastOrNull()?.date
        val suggested = if (!sameRule && lastOld != null && samePeriod(lastOld, today, rule.frequency)) {
            startOfNextPeriod(today, rule.frequency)
        } else {
            today
        }
        val effective = if (resetEffective || effectiveFrom == null || effectiveFrom < today) suggested else effectiveFrom
        return copy(suggestedEffectiveFrom = suggested, effectiveFrom = effective)
    }

    private fun samePeriod(a: LocalDate, b: LocalDate, frequency: Frequency): Boolean = when (frequency) {
        Frequency.WEEKLY -> mondayOf(a) == mondayOf(b)
        Frequency.MONTHLY -> a.yearMonth == b.yearMonth
        Frequency.YEARLY -> a.year == b.year
    }

    private fun startOfNextPeriod(date: LocalDate, frequency: Frequency): LocalDate = when (frequency) {
        Frequency.WEEKLY -> mondayOf(date).plus(DatePeriod(days = 7))
        Frequency.MONTHLY -> date.yearMonth.firstDay.plus(DatePeriod(months = 1))
        Frequency.YEARLY -> LocalDate(date.year + 1, 1, 1)
    }

    private fun mondayOf(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.ordinal))

    // ---- Lưu ----

    private fun save(backfill: Boolean?) {
        val state = currentState
        if (!state.canSave) return
        val amountMinor = MoneyInput.toMinor(state.amount, state.currency)
        if (amountMinor == null || amountMinor <= 0) {
            setState { copy(amountError = FieldError("amountMinor", RecurringErrorCode.INVALID_FIELD).toRecurringUiText()) }
            return
        }
        if (state.isNew && backfill == null) {
            val pastCount = pastOccurrences(state)
            if (pastCount > 0) {
                setState { copy(backfillPrompt = pastCount) }
                return
            }
        }
        setState { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            if (state.remindDaysBefore != null) requestReminderPermission()
            val result = if (state.isNew) create(state, amountMinor, backfill ?: true) else update(state, amountMinor)
            result.fold(
                onSuccess = { sendEffect(RecurringEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false).withError(error) } },
            )
        }
    }

    /** Số kỳ trước hôm nay trong cửa sổ bù 90 ngày (R7, R8). */
    private fun pastOccurrences(state: RecurringEditorState): Int {
        val today = state.today ?: return 0
        val start = state.startDate ?: return 0
        if (start >= today) return 0
        val recurring = state.hypothetical() ?: return 0
        val from = maxOf(start, today.minus(DatePeriod(days = RecurringLimits.CATCH_UP_DAYS - 1)))
        return RecurrenceEngine.occurrences(recurring, from, today.minus(DatePeriod(days = 1))).size
    }

    private suspend fun create(state: RecurringEditorState, amountMinor: Long, backfill: Boolean) = createRecurring(
        RecurringDraft(
            name = state.name,
            type = state.type,
            accountId = requireNotNull(state.accountId),
            categoryId = requireNotNull(state.categoryId),
            currency = state.currency,
            amountMinor = amountMinor,
            note = state.note.ifBlank { null },
            rule = state.rule(),
            startDate = state.startDate ?: requireNotNull(state.today),
            endDate = state.endDate,
            remindDaysBefore = state.remindDaysBefore,
            autoConfirm = state.autoConfirm,
            backfill = backfill,
        ),
    ).map { }

    private suspend fun update(state: RecurringEditorState, amountMinor: Long): AppResult<Unit> {
        val existing = requireNotNull(current)
        val today = requireNotNull(state.today)
        val revision = existing.currentRevision(today)
        val note = state.note.ifBlank { null }
        val rule = state.rule()
        val revisionChanged = revision.accountId != state.accountId || revision.categoryId != state.categoryId ||
            revision.amountMinor != amountMinor || revision.note != note || revision.rule != rule ||
            revision.autoConfirm != state.autoConfirm
        val detailsChanged = existing.name != state.name.trim() || existing.remindDaysBefore != state.remindDaysBefore
        val change = if (revisionChanged) {
            RevisionChange(
                effectiveFrom = state.effectiveFrom ?: today,
                accountId = requireNotNull(state.accountId),
                categoryId = requireNotNull(state.categoryId),
                amountMinor = amountMinor,
                note = note,
                rule = rule,
                autoConfirm = state.autoConfirm,
            )
        } else {
            null
        }
        return updateRecurring(existing.id, state.name, state.remindDaysBefore, detailsChanged, change)
    }

    private fun end() {
        val recurringId = args.recurringId ?: return
        val today = currentState.today ?: return
        setState { copy(endConfirmVisible = false, isSaving = true) }
        viewModelScope.launch {
            endRecurring(recurringId, today).fold(
                onSuccess = { sendEffect(RecurringEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false, error = error.toRecurringUiText()) } },
            )
        }
    }

    private fun delete() {
        val recurringId = args.recurringId ?: return
        setState { copy(deleteConfirmVisible = false, isSaving = true) }
        viewModelScope.launch {
            deleteRecurring(recurringId).fold(
                onSuccess = { sendEffect(RecurringEditorEffect.Close) },
                onFailure = { error -> setState { copy(isSaving = false, error = error.toRecurringUiText()) } },
            )
        }
    }

    private fun RecurringEditorState.withError(error: AppError): RecurringEditorState {
        val fields = (error as? AppError.Validation)?.fieldErrors.orEmpty()
        val nameError = fields.firstOrNull { it.field == "name" }?.toRecurringUiText()
        val amountError = fields.firstOrNull { it.field == "amountMinor" }?.toRecurringUiText()
        return copy(
            nameError = nameError,
            amountError = amountError,
            error = if (nameError != null || amountError != null) null else error.toRecurringUiText(),
        )
    }

    private companion object {
        const val PREVIEW_COUNT = 5
        const val PREVIEW_HORIZON_DAYS = 2_000
        const val LOOKBACK_DAYS = 400
    }
}
