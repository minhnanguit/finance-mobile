package com.uit.finance.core.network.api

import com.uit.finance.core.network.api.internal.GeneratedBudgetPayloadCodec
import com.uit.finance.core.network.api.model.BudgetPayload
import com.uit.finance.core.network.client.defaultJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.long

/** Payload ngân sách đi qua model `BudgetData` của contract 2.2.0: đúng tên field và kiểu server đọc. */
class BudgetPayloadCodecTest {

    private val codec = GeneratedBudgetPayloadCodec(defaultJson())

    private val budget = BudgetPayload(
        categoryId = "c1",
        currency = "VND",
        amountMinor = 1_000_000_000_000_000L,
        startMonth = LocalDate(2026, 10, 1),
    )

    @Test
    fun `đủ 4 field theo contract - số tiền là số nguyên - tháng là ngày ISO`() {
        val json = codec.encode(budget)

        assertEquals(setOf("categoryId", "currency", "amountMinor", "startMonth"), json.keys)
        assertEquals(1_000_000_000_000_000L, (json.getValue("amountMinor") as JsonPrimitive).long)
        assertEquals(JsonPrimitive("2026-10-01"), json.getValue("startMonth"))
        assertEquals(budget, codec.decodeBudget(json))
    }

    @Test
    fun `thiếu field bắt buộc thì báo lỗi thay vì đoán`() {
        assertFailsWith<IllegalArgumentException> {
            codec.decodeBudget(
                buildJsonObject {
                    put("categoryId", JsonPrimitive("c1"))
                    put("currency", JsonPrimitive("VND"))
                    put("startMonth", JsonPrimitive("2026-10-01"))
                },
            )
        }
    }

    @Test
    fun `toString không in số tiền`() {
        assertFalse("1000000000000000" in budget.toString())
    }
}
