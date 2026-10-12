@file:OptIn(ExperimentalTime::class)

package com.uit.finance.core.network.api

import com.uit.finance.core.network.api.internal.GeneratedSyncPayloadCodec
import com.uit.finance.core.network.api.model.AccountPayload
import com.uit.finance.core.network.api.model.CategoryPayload
import com.uit.finance.core.network.api.model.TransactionPayload
import com.uit.finance.core.network.client.defaultJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.long

/** Payload đi qua model sinh từ contract 2.1.0: tên field và kiểu phải đúng như server đọc. */
class SyncPayloadCodecTest {

    private val codec = GeneratedSyncPayloadCodec(defaultJson())

    private val transaction = TransactionPayload(
        type = "TRANSFER",
        status = "CONFIRMED",
        accountId = "a1",
        counterAccountId = "a2",
        amountMinor = 1_000_000_000_000_000L,
        currency = "VND",
        categoryId = null,
        occurredOn = LocalDate(2026, 10, 6),
        occurredAt = Instant.parse("2026-10-06T01:30:00Z"),
        payee = null,
        note = "chuyển",
    )

    @Test
    fun `giao dịch - tên field theo contract, số tiền giữ nguyên kiểu số nguyên, null bị bỏ`() {
        val json = codec.encode(transaction)

        assertEquals(
            setOf("type", "status", "accountId", "counterAccountId", "amountMinor", "currency", "occurredOn", "occurredAt", "note"),
            json.keys,
        )
        assertEquals(1_000_000_000_000_000L, (json.getValue("amountMinor") as JsonPrimitive).long)
        assertEquals(JsonPrimitive("2026-10-06"), json.getValue("occurredOn"))
        assertEquals(transaction, codec.decodeTransaction(json))
    }

    @Test
    fun `ví - đủ field bắt buộc kể cả archived`() {
        val account = AccountPayload("Ví", "EWALLET", "VND", -5_000, 3, archived = true)

        val json = codec.encode(account)

        assertEquals(setOf("name", "type", "currency", "openingBalanceMinor", "sortOrder", "archived"), json.keys)
        assertEquals(account, codec.decodeAccount(json))
    }

    @Test
    fun `danh mục - templateKey không bao giờ được gửi lên, nhưng đọc được khi server trả về`() {
        val sent = codec.encode(CategoryPayload("EXPENSE", "Phí", null, "percent", "#64748B", false, templateKey = "fee"))
        val received = codec.decodeCategory(
            buildJsonObject {
                put("kind", JsonPrimitive("EXPENSE"))
                put("name", JsonPrimitive("Phí giao dịch"))
                put("archived", JsonPrimitive(false))
                put("templateKey", JsonPrimitive("fee"))
            },
        )

        assertFalse("templateKey" in sent.keys)
        assertEquals("fee", received.templateKey)
    }

    @Test
    fun `enum lạ thì báo lỗi thay vì đoán`() {
        assertFailsWith<IllegalArgumentException> {
            codec.encode(AccountPayload("Ví", "CRYPTO", "VND", 0, 0, false))
        }
    }
}
