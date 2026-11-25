package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelMemoryGuardTest {
    @Test
    fun `required memory scales with model accelerator and context`() {
        val required = requiredModelMemoryBytes(
            modelBytes = 2_000_000_000L,
            acceleratorMemoryMb = 700,
            contextTokens = 8_000,
            kvBytesPerToken = 50_000,
        )
        assertEquals(2_000_000_000L + 700L * 1024 * 1024 + 8_000L * 50_000 + MODEL_LOAD_SAFETY_MARGIN_BYTES, required)
    }

    @Test
    fun `low available memory rejects while adequate or unknown memory allows`() {
        val required = requiredModelMemoryBytes(2_000_000_000L, 700, 8_000, 50_000)
        assertFalse(hasEnoughMemory(required - 1, required))
        assertTrue(hasEnoughMemory(required, required))
        assertTrue(hasEnoughMemory(Long.MAX_VALUE, required))
    }

    @Test
    fun `arithmetic saturates instead of overflowing`() {
        assertEquals(Long.MAX_VALUE, requiredModelMemoryBytes(Long.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
