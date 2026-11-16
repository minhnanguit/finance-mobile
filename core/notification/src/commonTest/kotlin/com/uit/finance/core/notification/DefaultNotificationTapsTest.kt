package com.uit.finance.core.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultNotificationTapsTest {
    @Test
    fun `tap remains pending until consumed`() {
        val taps = DefaultNotificationTaps()
        taps.onTapped("reminder.add_transaction")
        assertEquals("reminder.add_transaction", taps.pending.value)
        taps.consume()
        assertNull(taps.pending.value)
    }
}
