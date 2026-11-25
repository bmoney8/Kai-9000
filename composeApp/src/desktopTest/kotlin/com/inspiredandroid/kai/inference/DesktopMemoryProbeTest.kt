package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopMemoryProbeTest {
    @Test
    fun `desktop memory probes return finite measurements`() {
        assertNotEquals(
            Long.MAX_VALUE,
            getAvailableMemoryBytes(),
            "Desktop available-memory probe reports unbounded memory, so the LiteRT load guard cannot reject low-memory systems",
        )
        assertNotEquals(
            Long.MAX_VALUE,
            getTotalMemoryBytes(),
            "Desktop total-memory probe reports unbounded memory, so device performance classification is misleading",
        )
        assertTrue(getAvailableMemoryBytes() > 0L)
        assertTrue(getTotalMemoryBytes() > 0L)
    }
}
