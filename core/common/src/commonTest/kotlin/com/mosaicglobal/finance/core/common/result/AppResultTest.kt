package com.mosaicglobal.finance.core.common.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppResultTest {

    @Test
    fun mapTransformsSuccessOnly() {
        assertEquals(AppResult.Success(4), 2.asSuccess().map { it * 2 })
        val failure: AppResult<Int> = AppError.Unauthorized.asFailure()
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun flatMapShortCircuits() {
        val result = 2.asSuccess()
            .flatMap { AppError.Network("offline").asFailure() }
            .flatMap { it.toString().asSuccess() }
        assertEquals(AppError.Network("offline"), result.errorOrNull())
        assertNull(result.getOrNull())
    }

    @Test
    fun foldCoversBothBranches() {
        assertEquals("ok:1", 1.asSuccess().fold({ "ok:$it" }, { "err" }))
        assertEquals("err", AppError.Unknown().asFailure().fold({ "ok" }, { "err" }))
    }
}
