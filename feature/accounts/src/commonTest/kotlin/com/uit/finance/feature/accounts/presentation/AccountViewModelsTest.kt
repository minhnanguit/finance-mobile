@file:OptIn(ExperimentalCoroutinesApi::class)

package com.uit.finance.feature.accounts.presentation

import app.cash.turbine.test
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.FieldError
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.BalanceSummary
import com.uit.finance.core.ledger.domain.model.LedgerErrorCode
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeBalanceRepository
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.balance
import com.uit.finance.core.presentation.money.MoneyFormatter
import com.uit.finance.core.ui.ledger.toLedgerUiText
import com.uit.finance.feature.accounts.domain.usecase.DeleteAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.MoveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.ObserveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.ObserveAccountsOverviewUseCase
import com.uit.finance.feature.accounts.domain.usecase.SaveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.SetAccountArchivedUseCase
import com.uit.finance.feature.accounts.presentation.editor.AccountEditorArgs
import com.uit.finance.feature.accounts.presentation.editor.AccountEditorEffect
import com.uit.finance.feature.accounts.presentation.editor.AccountEditorIntent
import com.uit.finance.feature.accounts.presentation.editor.AccountEditorViewModel
import com.uit.finance.feature.accounts.presentation.list.AccountListEffect
import com.uit.finance.feature.accounts.presentation.list.AccountListIntent
import com.uit.finance.feature.accounts.presentation.list.AccountListViewModel
import com.uit.finance.feature.accounts.presentation.list.MoneyUi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class AccountViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    private val accounts = FakeAccountRepository()
    private val balances = FakeBalanceRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun listViewModel() = AccountListViewModel(
        observeOverview = ObserveAccountsOverviewUseCase(accounts, balances),
        setArchived = SetAccountArchivedUseCase(accounts),
        moveAccount = MoveAccountUseCase(accounts),
        deleteAccount = DeleteAccountUseCase(accounts),
        money = MoneyFormatter(),
    )

    private fun editorViewModel(accountId: String?) =
        AccountEditorViewModel(AccountEditorArgs(accountId), ObserveAccountUseCase(accounts), SaveAccountUseCase(accounts))

    @Test
    fun `danh sách format số dư - tổng và quyền đổi chỗ`() = runTest(dispatcher) {
        accounts.accounts.value = listOf(account("a", "Tiền mặt", sortOrder = 0), account("b", "Bank", sortOrder = 1))
        balances.balances.value = BalanceSummary(
            accounts = listOf(balance("a", 1_250_000), balance("b", -20_000)),
            totals = listOf(Money(1_230_000, "VND")),
        )
        val vm = listViewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(MoneyUi("VND", "1.230.000", isNegative = false)), state.totals)
        assertEquals(listOf(false, true), state.active.map { it.canMoveUp })
        assertEquals(listOf(true, false), state.active.map { it.canMoveDown })
        assertEquals(MoneyUi("VND", "20.000", isNegative = true), state.active[1].balance)
    }

    @Test
    fun `xoá ví đang dùng thì báo gợi ý lưu trữ`() = runTest(dispatcher) {
        accounts.accounts.value = listOf(account("a"))
        val vm = listViewModel()
        advanceUntilIdle()
        accounts.failNext = AppError.Validation("account", LedgerErrorCode.IN_USE)

        vm.onIntent(AccountListIntent.DeleteClicked("a"))
        assertEquals("a", vm.state.value.pendingDelete?.id)
        vm.onIntent(AccountListIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertEquals(null, vm.state.value.pendingDelete)
        assertEquals(FieldError("account", LedgerErrorCode.IN_USE).toLedgerUiText(), vm.state.value.message)
        assertEquals(1, accounts.accounts.value.size)
    }

    @Test
    fun `chạm thêm ví mở form tạo mới`() = runTest(dispatcher) {
        val vm = listViewModel()
        vm.effects.test {
            vm.onIntent(AccountListIntent.AddClicked)
            assertEquals(AccountListEffect.OpenEditor(null), awaitItem())
        }
    }

    @Test
    fun `form tạo ví đổi số tiền theo tiền tệ rồi lưu và đóng`() = runTest(dispatcher) {
        val vm = editorViewModel(null)
        assertFalse(vm.state.value.canSave)

        vm.onIntent(AccountEditorIntent.NameChanged("Thẻ Visa"))
        vm.onIntent(AccountEditorIntent.TypeChanged(AccountType.BANK))
        vm.onIntent(AccountEditorIntent.CurrencySelected("USD"))
        vm.onIntent(AccountEditorIntent.OpeningBalanceChanged("-12.5"))
        assertEquals("-12,5", vm.state.value.openingBalance)

        vm.effects.test {
            vm.onIntent(AccountEditorIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals(AccountEditorEffect.Close, awaitItem())
        }
        val saved = accounts.accounts.value.single()
        assertEquals(Money(-1_250, "USD"), saved.openingBalance)
        assertEquals(AccountType.BANK, saved.type)
    }

    @Test
    fun `form sửa điền sẵn và lỗi theo trường hiện dưới ô`() = runTest(dispatcher) {
        accounts.accounts.value = listOf(account("a", "Ví cũ", openingMinor = 50_000))
        val vm = editorViewModel("a")
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertEquals("Ví cũ", vm.state.value.name)
        assertEquals("50000", vm.state.value.openingBalance)

        accounts.failNext = AppError.Validation("currency", LedgerErrorCode.CURRENCY_LOCKED)
        vm.onIntent(AccountEditorIntent.SaveClicked)
        advanceUntilIdle()

        assertEquals(FieldError("currency", LedgerErrorCode.CURRENCY_LOCKED).toLedgerUiText(), vm.state.value.currencyError)
        assertEquals(null, vm.state.value.error)
        assertFalse(vm.state.value.isSaving)
    }
}
