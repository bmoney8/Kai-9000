package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelMemoryGuardTest {
    @Test
    fun `required memory scales with model accelerator and context`() {
        val required = requiredModelMemoryBytes(2_000_000_000L, 700, 8_000, 50_000)
        assertEquals(2_000_000_000L + 700L * 1024 * 1024 + 8_000L * 50_000 + MODEL_LOAD_SAFETY_MARGIN_BYTES, required)
    }

    @Test
    fun `low available memory rejects and sufficient or unknown memory allows`() {
        val required = requiredModelMemoryBytes(2_000_000_000L, 700, 8_000, 50_000)
        assertFalse(hasEnoughMemory(required - 1, required))
        assertTrue(hasEnoughMemory(required, required))
        assertTrue(hasEnoughMemory(Long.MAX_VALUE, required))
    }

    @Test
    fun `guard skips allocation when memory is low and invokes it when sufficient`() {
        var allocations = 0
        assertFailsWith<InsufficientMemoryException> {
            loadJvmEngineWithMemoryGuard(99, 100) { allocations++; "loaded" }
        }
        assertEquals(0, allocations)
        assertEquals("loaded", loadJvmEngineWithMemoryGuard(100, 100) { allocations++; "loaded" })
        assertEquals(1, allocations)
    }

    @Test
    fun `memory normalization uses unknown sentinel only for unavailable or invalid data`() {
        assertEquals(Long.MAX_VALUE, finiteMemoryOrUnknown(-1))
        assertEquals(Long.MAX_VALUE, finiteMemoryOrUnknown(0))
        assertEquals(0L, finiteMemoryOrUnknown(0, zeroIsValid = true))
        assertEquals(Long.MAX_VALUE, iosAvailableMemoryBytes(0))
        assertEquals(Long.MAX_VALUE, iosAvailableMemoryBytes(-1))
        assertEquals(64L, iosAvailableMemoryBytes(64))
        assertEquals(Long.MAX_VALUE, finiteMemoryOrUnknown(Long.MAX_VALUE))
        assertEquals(42L, finiteMemoryOrUnknown(42))
    }

    @Test
    fun `arithmetic saturates instead of overflowing`() {
        assertEquals(Long.MAX_VALUE, requiredModelMemoryBytes(Long.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
