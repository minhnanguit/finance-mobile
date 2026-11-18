package com.uit.finance.feature.settings.domain

import com.uit.finance.core.common.result.AppError
import com.uit.finance.core.common.result.AppResult
import com.uit.finance.feature.settings.domain.model.AccountDeletion
import com.uit.finance.feature.settings.domain.model.DeletionConfirmation
import com.uit.finance.feature.settings.domain.usecase.DeleteAccountUseCase
import com.uit.finance.feature.settings.testing.FakeAccountRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DeleteAccountUseCaseTest {

    private val repository = FakeAccountRepository()

    @Test
    fun `chữ xác nhận nhận mọi kiểu gõ - có dấu không dấu NFD hoa thường và khoảng trắng`() {
        val nfd = "XOÁ" // "Á" gõ bằng dấu kết hợp
        listOf("XOÁ", "xoá", "XOA", "xoa", " Xoá ", nfd).forEach { input ->
            assertTrue(DeletionConfirmation.matches(input), "phải nhận '$input'")
        }
        listOf("", "XO", "XOÁ!", "XÓA TÀI KHOẢN", "DELETE").forEach { input ->
            assertFalse(DeletionConfirmation.matches(input), "không được nhận '$input'")
        }
    }

    @Test
    fun `chữ xác nhận sai thì dừng ngay - không mở trình duyệt`() = runTest {
        val result = DeleteAccountUseCase(repository)("XO")

        assertIs<AppError.Validation>((result as AppResult.Failure).error)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun `chữ xác nhận đúng thì giao cho repository`() = runTest {
        val result = DeleteAccountUseCase(repository)("xoa")

        assertEquals(AppResult.Success(AccountDeletion.Deleted), result)
        assertEquals(1, repository.deleteCalls)
    }
}
