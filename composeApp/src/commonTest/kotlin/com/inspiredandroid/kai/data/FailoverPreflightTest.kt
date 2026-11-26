package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.ServiceCredentials
import com.inspiredandroid.kai.network.UiError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FailoverPreflightTest {

    private class FakeProbe(var reachable: Boolean) : EndpointHealthProbe {
        var callCount: Int = 0
            private set

        override suspend fun isReachable(credentials: ServiceCredentials, timeoutMs: Long): Boolean {
            callCount++
            assertEquals("http://localhost:11434/v1", credentials.baseUrl)
            assertEquals(1_500L, timeoutMs)
            return reachable
        }
    }

    private val remote = FallbackEntry(instanceId = "omniroute", service = Service.OpenAICompatible)
    private val local = FallbackEntry(instanceId = "litert", service = Service.LiteRT)
    private val chain = listOf(remote, local)

    private fun preflight(
        probe: FakeProbe,
        enabled: Boolean = true,
        hasLocalImage: Boolean = false,
    ) = FailoverPreflight(
        isEnabled = { enabled },
        timeoutMs = { 1_500L },
        probe = probe,
        hasLocalImageCapability = { hasLocalImage },
    )

    private fun credentialsOf(entry: FallbackEntry): ServiceCredentials = ServiceCredentials(
        baseUrl = "http://localhost:11434/v1",
    )

    @Test
    fun `unreachable remote primary fails over to LiteRT text`() = runTest {
        val probe = FakeProbe(reachable = false)
        val result = preflight(probe).apply(
            entries = chain,
            serviceOf = { it.service },
            credentialsOf = ::credentialsOf,
        )

        assertEquals(1, probe.callCount)
        assertEquals(listOf(Service.LiteRT), result.entries.map { it.service })
        assertEquals(FailoverRoute.LOCAL_TEXT, result.decision?.route)
        assertTrue(result.decision!!.tooltipText.contains("unreachable"))
        assertTrue(result.decision!!.tooltipText.contains(Service.LiteRT.displayName))
    }

    @Test
    fun `local image capability adds image to the failover route`() = runTest {
        val probe = FakeProbe(reachable = false)
        val result = preflight(probe, hasLocalImage = true).apply(
            entries = chain,
            serviceOf = { it.service },
            credentialsOf = ::credentialsOf,
        )

        assertEquals(FailoverRoute.LOCAL_TEXT_AND_IMAGE, result.decision?.route)
        assertTrue(result.decision!!.tooltipText.contains("text + image"))
    }

    @Test
    fun `reachable remote primary keeps the configured chain`() = runTest {
        val probe = FakeProbe(reachable = true)
        val result = preflight(probe).apply(
            entries = chain,
            serviceOf = { it.service },
            credentialsOf = ::credentialsOf,
        )

        assertEquals(1, probe.callCount)
        assertEquals(chain, result.entries)
        assertNull(result.decision)
    }

    @Test
    fun `failover is reversible`() = runTest {
        val probe = FakeProbe(reachable = false)
        val policy = preflight(probe)
        policy.apply(entries = chain, serviceOf = { it.service }, credentialsOf = ::credentialsOf)

        probe.reachable = true
        val nextTurn = policy.apply(
            entries = chain,
            serviceOf = { it.service },
            credentialsOf = ::credentialsOf,
        )

        assertEquals(chain, nextTurn.entries)
        assertNull(nextTurn.decision)
    }

    @Test
    fun `disabled failover skips the probe`() = runTest {
        val probe = FakeProbe(reachable = false)
        val result = preflight(probe, enabled = false).apply(
            entries = chain,
            serviceOf = { it.service },
            credentialsOf = ::credentialsOf,
        )

        assertEquals(0, probe.callCount)
        assertEquals(chain, result.entries)
    }

    @Test
    fun `non OpenAI-compatible primary is never probed`() = runTest {
        val probe = FakeProbe(reachable = false)
        val entries = listOf(FallbackEntry("free", Service.Free), local)
        val result = preflight(probe).apply(
            entries = entries,
            serviceOf = { it.service },
            credentialsOf = ::credentialsOf,
        )

        assertEquals(0, probe.callCount)
        assertEquals(entries, result.entries)
    }

    @Test
    fun `no on-device target means no preflight probe`() = runTest {
        val probe = FakeProbe(reachable = false)
        val result = preflight(probe).apply(
            entries = listOf(remote),
            serviceOf = { it.service },
            credentialsOf = ::credentialsOf,
        )

        assertEquals(0, probe.callCount)
        assertEquals(listOf(remote), result.entries)
    }

    @Test
    fun `image route is never selected without a local text target`() {
        assertEquals(null, selectFailoverRoute(hasLocalText = false, hasLocalImage = true))
        assertEquals(FailoverRoute.LOCAL_TEXT, selectFailoverRoute(true, false))
    }

    @Test
    fun `routing decision wins over generic fallback status text`() {
        val status = FallbackStatus(
            serviceName = Service.OpenAICompatible.displayName,
            errorReason = UiError.Text("Cannot connect to server"),
            nextServiceName = Service.LiteRT.displayName,
            routingDecision = "OpenAI-Compatible API unreachable → Local Model (text)",
        )

        assertEquals(
            status.routingDecision,
            status.tooltipText(
                failedText = "OpenAI-Compatible API: Cannot connect to server",
                tryingNextText = "Trying Local Model…",
            ),
        )
    }
}
