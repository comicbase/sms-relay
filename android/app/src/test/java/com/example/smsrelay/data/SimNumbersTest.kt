package com.example.smsrelay.data

import org.junit.Assert.*
import org.junit.Test

class SimNumbersTest {
    @Test fun mapsEachSlotAndPreservesInternationalNumbers() {
        val numbers = SimNumbers(" +8613800000000 ", "09000000000")
        assertEquals("+8613800000000", numbers.recipientFor(0))
        assertEquals("09000000000", numbers.recipientFor(1))
        assertTrue(numbers.isValid())
    }

    @Test fun neverGuessesUnknownSlotsOrUnconfiguredNumbers() {
        val numbers = SimNumbers("13800000000", "  ")
        assertNull(numbers.recipientFor(null))
        assertNull(numbers.recipientFor(-1))
        assertNull(numbers.recipientFor(2))
        assertNull(numbers.recipientFor(1))
    }

    @Test fun validatesBothSlotsAndAllowsClearing() {
        assertTrue(SimNumbers().isValid())
        listOf("12", "abc", "+", "1234567890123456", "123-456", "123 456").forEach {
            assertFalse(SimNumbers(sim1 = it).isValid())
            assertFalse(SimNumbers(sim2 = it).isValid())
        }
    }

    @Test fun capturedRecipientDoesNotChangeWithConfiguration() {
        val before = SimNumbers("13800000000", "13900000000")
        val captured = before.recipientFor(0)
        val after = before.copy(sim1 = "13700000000")
        assertEquals("13800000000", captured)
        assertEquals("13700000000", after.recipientFor(0))
    }
}
