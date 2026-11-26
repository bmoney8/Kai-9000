package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopMemoryProbeTest {
    @Test
    fun `desktop memory probes return host measurements`() {
        assertTrue(getAvailableMemoryBytes() >= 0L)
        assertTrue(getTotalMemoryBytes() > 0L)
        assertNotEquals(Long.MAX_VALUE, getAvailableMemoryBytes())
        assertNotEquals(Long.MAX_VALUE, getTotalMemoryBytes())
    }

    @Test
    fun `desktop memory fallback handles unavailable and invalid bean values`() {
        assertEquals(Long.MAX_VALUE, desktopAvailableMemoryBytes(null))
        assertEquals(Long.MAX_VALUE, desktopTotalMemoryBytes(null))
        assertEquals(Long.MAX_VALUE, desktopAvailableMemoryBytes(-1L))
        assertEquals(Long.MAX_VALUE, desktopTotalMemoryBytes(0L))
        assertEquals(0L, desktopAvailableMemoryBytes(0L))
        assertEquals(32_000L, desktopTotalMemoryBytes(32_000L))
    }

    @Test
    fun `desktop guard blocks allocation at low headroom and permits adequate headroom`() {
        var allocations = 0
        kotlin.test.assertFailsWith<InsufficientMemoryException> {
            loadJvmEngineWithMemoryGuard(99, 100) { allocations++; "loaded" }
        }
        assertEquals(0, allocations)
        assertEquals("loaded", loadJvmEngineWithMemoryGuard(100, 100) { allocations++; "loaded" })
        assertEquals(1, allocations)
    }
}
