package com.inspiredandroid.kai.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EndpointHealthProbeTest {

    @Test
    fun `successful models fetch is reachable`() = runTest {
        assertEquals(true, probeWith(timeoutMs = 1_000L) { Result.success(Unit) })
    }

    @Test
    fun `failed models fetch is unreachable`() = runTest {
        assertEquals(
            false,
            probeWith(timeoutMs = 1_000L) { Result.failure(IllegalStateException("connection refused")) },
        )
    }

    @Test
    fun `slow models fetch times out as unreachable`() = runTest {
        assertEquals(false, probeWith(timeoutMs = 1L) { delay(10_000L); Result.success(Unit) })
    }

    @Test
    fun `caller cancellation is not converted into an outage`() = runTest {
        assertFailsWith<CancellationException> {
            probeWith(timeoutMs = 1_000L) { throw CancellationException("stopped") }
        }
    }
}
