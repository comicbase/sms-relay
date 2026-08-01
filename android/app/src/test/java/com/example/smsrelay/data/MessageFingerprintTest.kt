package com.example.smsrelay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessageFingerprintTest {
    @Test fun sameMessageProducesSameFingerprint() {
        val first = MessageFingerprint.create("10086", "hello", 1234L, 1)
        val second = MessageFingerprint.create("10086", "hello", 1234L, 1)
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test fun differentSimProducesDifferentFingerprint() {
        assertNotEquals(
            MessageFingerprint.create("10086", "hello", 1234L, 1),
            MessageFingerprint.create("10086", "hello", 1234L, 2),
        )
    }
}
