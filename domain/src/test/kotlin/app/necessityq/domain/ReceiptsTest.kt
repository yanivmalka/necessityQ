package app.necessityq.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptsTest {
    private val m = ItemMessage("1", "milk", "חלב", "me", "אני", "איזה?", 100)
    private val members = listOf("me", "keren")

    @Test fun `ticks progress from written to read`() {
        assertEquals(DeliveryStatus.PENDING, Receipts.status(m.copy(pending = true), "me", emptyMap(), members))
        assertEquals(DeliveryStatus.SENT, Receipts.status(m, "me", emptyMap(), members))
        assertEquals(DeliveryStatus.DELIVERED, Receipts.status(m, "me", mapOf("keren" to Receipt(seenAt = 150)), members))
        assertEquals(DeliveryStatus.READ, Receipts.status(m, "me", mapOf("keren" to Receipt(read = mapOf("milk" to 120))), members))
    }
}
