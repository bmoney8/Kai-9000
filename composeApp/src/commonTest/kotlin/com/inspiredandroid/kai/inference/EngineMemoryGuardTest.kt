package com.inspiredandroid.kai.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FakeEngine {
    var initialized = false
    fun init() { initialized = true }
}

class EngineMemoryGuardTest {
    private val modelBytes = 8_000_000_000L
    private val acceleratorMemoryMb = 1_024
    private val contextTokens = 4_096
    private val kvBytesPerToken = 65_536
    private val requiredBytes = requiredModelMemoryBytes(
        modelBytes,
        acceleratorMemoryMb,
        contextTokens,
        kvBytesPerToken,
    )

    @Test
    fun `JVM load probes memory and rejects before native engine allocation`() {
        val fakeEngine = FakeEngine()
        var probeCalls = 0

        assertFailsWith<InsufficientMemoryException> {
            loadJvmLiteRtEngine(
                modelBytes,
                acceleratorMemoryMb,
                contextTokens,
                kvBytesPerToken,
                availableMemory = { probeCalls++; requiredBytes - 1 },
            ) { fakeEngine.init() }
        }

        assertEquals(1, probeCalls)
        assertFalse(fakeEngine.initialized)
    }

    @Test
    fun `JVM load reaches native engine allocation with sufficient memory`() {
        val fakeEngine = FakeEngine()
        var probeCalls = 0

        loadJvmLiteRtEngine(
            modelBytes,
            acceleratorMemoryMb,
            contextTokens,
            kvBytesPerToken,
            availableMemory = { probeCalls++; requiredBytes },
        ) { fakeEngine.init() }

        assertEquals(1, probeCalls)
        assertTrue(fakeEngine.initialized)
    }

    @Test
    fun `iOS load probes memory and rejects before native bridge allocation`() = runTest {
        val fakeBridge = FakeEngine()
        var probeCalls = 0

        assertFailsWith<InsufficientMemoryException> {
            loadIosLiteRtEngine(
                modelBytes,
                acceleratorMemoryMb,
                contextTokens,
                kvBytesPerToken,
                availableMemory = { probeCalls++; requiredBytes - 1 },
            ) { fakeBridge.init() }
        }

        assertEquals(1, probeCalls)
        assertFalse(fakeBridge.initialized)
    }

    @Test
    fun `iOS load reaches native bridge allocation with sufficient memory`() = runTest {
        val fakeBridge = FakeEngine()
        var probeCalls = 0

        loadIosLiteRtEngine(
            modelBytes,
            acceleratorMemoryMb,
            contextTokens,
            kvBytesPerToken,
            availableMemory = { probeCalls++; requiredBytes },
        ) { fakeBridge.init() }

        assertEquals(1, probeCalls)
        assertTrue(fakeBridge.initialized)
    }
}