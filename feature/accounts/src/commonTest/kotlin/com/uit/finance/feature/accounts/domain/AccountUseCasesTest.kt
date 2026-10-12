package com.uit.finance.feature.accounts.domain

import app.cash.turbine.test
import com.uit.finance.core.common.money.Money
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.core.ledger.domain.model.AccountType
import com.uit.finance.core.ledger.domain.model.BalanceSummary
import com.uit.finance.core.ledger.testing.FakeAccountRepository
import com.uit.finance.core.ledger.testing.FakeBalanceRepository
import com.uit.finance.core.ledger.testing.account
import com.uit.finance.core.ledger.testing.balance
import com.uit.finance.feature.accounts.domain.model.AccountInput
import com.uit.finance.feature.accounts.domain.model.MoveDirection
import com.uit.finance.feature.accounts.domain.usecase.MoveAccountUseCase
import com.uit.finance.feature.accounts.domain.usecase.ObserveAccountsOverviewUseCase
import com.uit.finance.feature.accounts.domain.usecase.SaveAccountUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/* Tên test không dùng dấu phẩy / hai chấm / ngoặc: Kotlin/Native không cho phép trong identifier. */
class AccountUseCasesTest {

    private val accounts = FakeAccountRepository()
    private val balances = FakeBalanceRepository()

    @Test
    fun `tổng quan tách ví lưu trữ và ghép số dư`() = runTest {
        accounts.accounts.value = listOf(account("a", sortOrder = 0), account("b", archived = true, sortOrder = 1))
        balances.balances.value = BalanceSummary(listOf(balance("a", 500)), totals = listOf(Money(500, "VND")))

        ObserveAccountsOverviewUseCase(accounts, balances)().test {
            val overview = awaitItem()
            assertEquals(listOf("a"), overview.active.map { it.account.id })
            assertEquals(Money(500, "VND"), overview.active.single().balance)
            assertEquals(listOf("b"), overview.archived.map { it.account.id })
            assertEquals(listOf(Money(500, "VND")), overview.totals)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ví mới đứng cuối danh sách`() = runTest {
        accounts.accounts.value = listOf(account("a", sortOrder = 0), account("b", sortOrder = 4))

        val created = SaveAccountUseCase(accounts)(AccountInput(null, "Ví mới", AccountType.BANK, "USD", 1_000))

        assertIs<AppResult.Success<*>>(created)
        assertEquals(5, accounts.accounts.value.last().sortOrder)
        assertEquals(Money(1_000, "USD"), accounts.accounts.value.last().openingBalance)
    }

    @Test
    fun `sửa ví giữ vị trí và trạng thái lưu trữ`() = runTest {
        accounts.accounts.value = listOf(account("a", sortOrder = 3, archived = true))

        SaveAccountUseCase(accounts)(AccountInput("a", "Đổi tên", AccountType.EWALLET, "VND", 9))

        val saved = accounts.accounts.value.single()
        assertEquals("Đổi tên", saved.name)
        assertEquals(3, saved.sortOrder)
        assertEquals(true, saved.archived)
    }

    @Test
    fun `đổi chỗ với ví kề bên và đẩy ví lưu trữ xuống cuối`() = runTest {
        accounts.accounts.value = listOf(
            account("a", sortOrder = 0),
            account("old", sortOrder = 1, archived = true),
            account("b", sortOrder = 2),
            account("c", sortOrder = 3),
        )
        val move = MoveAccountUseCase(accounts)

        move("b", MoveDirection.Up)
        assertEquals(listOf("b", "a", "c", "old"), accounts.observeAccounts().first().map { it.id })

        move("b", MoveDirection.Up) // đã ở đầu: không đổi
        assertEquals(listOf("b", "a", "c", "old"), accounts.observeAccounts().first().map { it.id })
    }
}
