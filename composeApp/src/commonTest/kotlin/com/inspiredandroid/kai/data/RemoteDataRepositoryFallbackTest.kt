package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.OpenAICompatibleConnectionException
import com.inspiredandroid.kai.network.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteDataRepositoryFallbackTest {

    private class FakeProvider(
        override val entry: FallbackEntry,
        private val block: suspend () -> String,
    ) : FallbackTurnProvider<String> {
        var callCount: Int = 0
            private set

        override suspend fun respond(): String {
            callCount++
            return block()
        }
    }

    /** The production Omniroute → LiteRT chain, built with an available local engine. */
    private fun omnirouteToLiteRtChain(): List<FallbackEntry> = buildFallbackChain(
        instances = listOf(
            ServiceInstance(instanceId = "omniroute", serviceId = Service.OpenAICompatible.id),
            ServiceInstance(instanceId = "litert", serviceId = Service.LiteRT.id),
        ),
        isFreeServicePrimary = false,
        isFreeFallbackEnabled = false,
        hasLocalInferenceEngine = true,
    )

    private fun traversal(
        providers: List<FallbackTurnProvider<String>>,
        statuses: MutableList<FallbackStatus>,
        persisted: MutableList<FallbackTurnResult<String>>,
        onDone: suspend () -> Unit,
    ): FallbackTurnTraversal<String> = FallbackTurnTraversal(
        providers = providers,
        onFallback = { failed, error, next ->
            statuses += FallbackStatus(
                serviceName = failed.entry.service.displayName,
                errorReason = error.toUiError(),
                nextServiceName = next?.entry?.service?.displayName,
            )
        },
        onSuccess = { result -> persisted += result },
        onDone = onDone,
    )

    @Test
    fun `on-device provider stays after remote provider when engine available`() {
        val chain = buildFallbackChain(
            instances = listOf(
                ServiceInstance(instanceId = "remote", serviceId = Service.OpenAICompatible.id),
                ServiceInstance(instanceId = "litert", serviceId = Service.LiteRT.id),
            ),
            isFreeServicePrimary = false,
            isFreeFallbackEnabled = false,
            hasLocalInferenceEngine = true,
        )

        assertEquals(
            listOf(Service.OpenAICompatible.id, Service.LiteRT.id),
            chain.map { it.service.id },
        )
    }

    @Test
    fun `on-device provider excluded when local inference engine unavailable`() {
        val chain = buildFallbackChain(
            instances = listOf(
                ServiceInstance(instanceId = "remote", serviceId = Service.OpenAICompatible.id),
                ServiceInstance(instanceId = "litert", serviceId = Service.LiteRT.id),
            ),
            isFreeServicePrimary = false,
            isFreeFallbackEnabled = false,
            hasLocalInferenceEngine = false,
        )

        assertEquals(listOf(Service.OpenAICompatible.id), chain.map { it.service.id })
    }

    @Test
    fun `free tier appended after on-device fallback when enabled`() {
        val chain = buildFallbackChain(
            instances = listOf(
                ServiceInstance(instanceId = "remote", serviceId = Service.OpenAICompatible.id),
                ServiceInstance(instanceId = "litert", serviceId = Service.LiteRT.id),
            ),
            isFreeServicePrimary = false,
            isFreeFallbackEnabled = true,
            hasLocalInferenceEngine = true,
        )

        assertEquals(
            listOf(Service.OpenAICompatible.id, Service.LiteRT.id, Service.Free.id),
            chain.map { it.service.id },
        )
    }

    @Test
    fun `remote network failure retries the turn on configured LiteRT and persists its answer`() = runTest {
        val chain = omnirouteToLiteRtChain()
        val remote = FakeProvider(chain[0]) { throw OpenAICompatibleConnectionException() }
        val local = FakeProvider(chain[1]) { "on-device answer" }
        val statuses = mutableListOf<FallbackStatus>()
        val persisted = mutableListOf<FallbackTurnResult<String>>()
        var statusCleared = 0

        val result = traversal(listOf(remote, local), statuses, persisted) { statusCleared++ }.run()

        assertEquals(1, remote.callCount)
        assertEquals(1, local.callCount)
        assertEquals("on-device answer", result.turn)
        assertEquals("litert", result.entry.instanceId)
        assertTrue(result.isFallback)
        assertEquals(1, statuses.size)
        assertEquals(Service.OpenAICompatible.displayName, statuses.single().serviceName)
        assertEquals(Service.LiteRT.displayName, statuses.single().nextServiceName)
        assertEquals(1, statusCleared)
        assertEquals(1, persisted.size)
        assertEquals("on-device answer", persisted.single().turn)
        assertEquals("litert", persisted.single().entry.instanceId)
        assertTrue(persisted.single().isFallback)
    }

    @Test
    fun `cancellation during remote call stops fallback before LiteRT and persists nothing`() = runTest {
        val chain = omnirouteToLiteRtChain()
        val remote = FakeProvider(chain[0]) { throw CancellationException("cancelled by user") }
        val local = FakeProvider(chain[1]) { "should never run" }
        val statuses = mutableListOf<FallbackStatus>()
        val persisted = mutableListOf<FallbackTurnResult<String>>()
        var statusCleared = 0

        assertFailsWith<CancellationException> {
            traversal(listOf(remote, local), statuses, persisted) { statusCleared++ }.run()
        }

        assertEquals(1, remote.callCount)
        assertEquals(0, local.callCount)
        assertTrue(statuses.isEmpty())
        assertTrue(persisted.isEmpty())
        assertEquals(1, statusCleared)
    }
}
