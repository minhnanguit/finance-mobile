@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.recurring.presentation

import app.cash.turbine.test
import com.uit.finance.core.ledger.domain.model.CategoryKind
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeCategoryRepository
import com.uit.finance.core.ledger.testing.NOON_2023_11_14
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.category
import com.uit.finance.core.recurring.domain.model.RecurrenceRule
import com.uit.finance.core.recurring.testing.FakeRecurringRepository
import com.uit.finance.core.testing.TestClock
import com.uit.finance.feature.recurring.FakePermissionGate
import com.uit.finance.feature.recurring.TODAY
import com.uit.finance.feature.recurring.domain.usecase.CreateRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.DeleteRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.EndRecurringUseCase
import com.uit.finance.feature.recurring.domain.usecase.ObserveRecurringFormDataUseCase
import com.uit.finance.feature.recurring.domain.usecase.RequestReminderPermissionUseCase
import com.uit.finance.feature.recurring.domain.usecase.UpdateRecurringUseCase
import com.uit.finance.feature.recurring.presentation.editor.EditorPicker
import com.uit.finance.feature.recurring.presentation.editor.RecurringEditorArgs
import com.uit.finance.feature.recurring.presentation.editor.RecurringEditorEffect
import com.uit.finance.feature.recurring.presentation.editor.RecurringEditorIntent
import com.uit.finance.feature.recurring.presentation.editor.RecurringEditorViewModel
import com.uit.finance.feature.recurring.rent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc / dấu chấm: Kotlin/Native không cho phép trong identifier. */
class RecurringEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val recurrings = FakeRecurringRepository()
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val permission = FakePermissionGate()
    private val clock = TestClock(NOON_2023_11_14)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        accounts.accounts.value = listOf(account("cash", currency = "VND"), account("card", currency = "USD"))
        categories.categories.value = listOf(category("home", "Nhà ở"), category("salary", "Lương", kind = CategoryKind.INCOME))
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** ViewModel đăng ký `observeToday` (đợi tới nửa đêm): huỷ scope sau mỗi test để runTest không chờ mãi. */
    private fun TestScope.viewModel(recurringId: String? = null): RecurringEditorViewModel {
        val vm = RecurringEditorViewModel(
            args = RecurringEditorArgs(recurringId),
            observeFormData = ObserveRecurringFormDataUseCase(recurrings, accounts, categories, clock),
            createRecurring = CreateRecurringUseCase(recurrings),
            updateRecurring = UpdateRecurringUseCase(recurrings),
            endRecurring = EndRecurringUseCase(recurrings),
            deleteRecurring = DeleteRecurringUseCase(recurrings),
            requestReminderPermission = RequestReminderPermissionUseCase(permission),
        )
        return vm
    }

    private suspend fun TestScope.runVm(block: suspend TestScope.(RecurringEditorViewModel) -> Unit, recurringId: String? = null) {
        val vm = viewModel(recurringId)
        try {
            runCurrent()
            block(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `tạo mới - mặc định ví đầu tiên - danh mục chi - bắt đầu hôm nay - ngày trong tháng là hôm nay`() = runTest(dispatcher) {
        runVm({ vm ->
            val state = vm.state.value
            assertEquals("cash", state.accountId)
            assertEquals("VND", state.currency)
            assertEquals("home", state.categoryId)
            assertEquals(TODAY, state.startDate)
            assertEquals(14, state.dayOfMonth)
            assertEquals("14/11/2023", state.preview.first())
        })
    }

    @Test
    fun `bắt đầu trong quá khứ - hỏi ghi bù - chọn chỉ từ hôm nay thì tạo với backfill false`() = runTest(dispatcher) {
        runVm({ vm ->
            vm.onIntent(RecurringEditorIntent.NameChanged("Tiền nhà"))
            vm.onIntent(RecurringEditorIntent.AmountChanged("3000000"))
            vm.onIntent(RecurringEditorIntent.OptionSelected(EditorPicker.DAY, "5"))
            vm.onIntent(RecurringEditorIntent.DateSelected(EditorPicker.START_DATE, LocalDate(2023, 9, 1)))

            vm.onIntent(RecurringEditorIntent.SaveClicked)
            assertEquals(3, vm.state.value.backfillPrompt) // 05/09 - 05/10 - 05/11 nằm trong 90 ngày

            vm.effects.test {
                vm.onIntent(RecurringEditorIntent.BackfillAnswered(false))
                runCurrent()
                assertEquals(RecurringEditorEffect.Close, awaitItem())
            }
            val created = recurrings.created.single()
            assertFalse(created.backfill)
            assertEquals(RecurrenceRule.monthly(5), created.rule)
            assertEquals(1, permission.requests, "bật nhắc thì xin quyền một lần")
        })
    }

    @Test
    fun `sửa số tiền - phiên bản mới áp từ hôm nay - tên không đổi thì không ghi chi tiết`() = runTest(dispatcher) {
        recurrings.recurrings.value = listOf(rent())
        runVm({ vm ->
            vm.onIntent(RecurringEditorIntent.AmountChanged("3200000"))
            vm.effects.test {
                vm.onIntent(RecurringEditorIntent.SaveClicked)
                runCurrent()
                assertEquals(RecurringEditorEffect.Close, awaitItem())
            }
            val (id, change) = recurrings.revised.single()
            assertEquals("rent", id)
            assertEquals(TODAY, change.effectiveFrom)
            assertEquals(3_200_000, change.amountMinor)
        }, recurringId = "rent")
    }

    @Test
    fun `đổi lịch khi tháng này đã có kỳ - gợi ý hiệu lực từ đầu tháng sau - chọn sớm hơn thì cảnh báo`() = runTest(dispatcher) {
        recurrings.recurrings.value = listOf(rent(day = 5))
        runVm({ vm ->
            vm.onIntent(RecurringEditorIntent.OptionSelected(EditorPicker.DAY, "20"))

            assertEquals(LocalDate(2023, 12, 1), vm.state.value.effectiveFrom)
            assertFalse(vm.state.value.effectiveFromWarning)
            assertEquals("20/12/2023", vm.state.value.preview.first())

            vm.onIntent(RecurringEditorIntent.DateSelected(EditorPicker.EFFECTIVE_DATE, TODAY))
            assertTrue(vm.state.value.effectiveFromWarning)
            assertEquals("20/11/2023", vm.state.value.preview.first())
        }, recurringId = "rent")
    }

    @Test
    fun `khoản bị xoá ở máy khác trong lúc mở form - khoá lưu và báo lỗi`() = runTest(dispatcher) {
        recurrings.recurrings.value = listOf(rent())
        runVm({ vm ->
            recurrings.recurrings.value = emptyList()
            runCurrent()

            assertTrue(vm.state.value.isDeletedElsewhere)
            assertFalse(vm.state.value.canSave)
        }, recurringId = "rent")
    }

    @Test
    fun `kết thúc từ hôm nay - gọi end với ngày hôm nay`() = runTest(dispatcher) {
        recurrings.recurrings.value = listOf(rent())
        runVm({ vm ->
            vm.onIntent(RecurringEditorIntent.EndClicked)
            vm.effects.test {
                vm.onIntent(RecurringEditorIntent.EndConfirmed)
                runCurrent()
                assertEquals(RecurringEditorEffect.Close, awaitItem())
            }
            assertEquals(listOf("rent" to TODAY), recurrings.ended)
        }, recurringId = "rent")
    }
}
