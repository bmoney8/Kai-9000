package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Platform-independent tests for the exact guard and normalization functions used by iOS. */
class IosMemoryGuardTest {
    @Test
    fun `iOS available-memory normalization handles measured and unavailable values`() {
        assertEquals(256L * 1024 * 1024, iosAvailableMemoryBytes(256L * 1024 * 1024))
        assertEquals(Long.MAX_VALUE, iosAvailableMemoryBytes(0))
        assertEquals(Long.MAX_VALUE, iosAvailableMemoryBytes(-1))
    }

    @Test
    fun `iOS bridge guard skips allocation when memory is low and invokes when sufficient`() {
        var bridgeAllocations = 0
        assertFailsWith<InsufficientMemoryException> {
            loadIosBridgeWithMemoryGuard(99, 100) { bridgeAllocations++; "loaded" }
        }
        assertEquals(0, bridgeAllocations)
        assertEquals("loaded", loadIosBridgeWithMemoryGuard(100, 100) { bridgeAllocations++; "loaded" })
        assertEquals(1, bridgeAllocations)
    }
}
