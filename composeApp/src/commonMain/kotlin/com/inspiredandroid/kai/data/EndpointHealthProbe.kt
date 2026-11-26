package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.network.ServiceCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Bounded reachability check for a configured remote endpoint.
 *
 * The probe is deliberately separate from [Requests] so a turn can be tested without touching
 * the network. Timeouts and connection failures mean "unreachable"; a real cancellation is
 * rethrown so a stopped turn is never mistaken for an outage.
 */
internal interface EndpointHealthProbe {
    suspend fun isReachable(credentials: ServiceCredentials, timeoutMs: Long): Boolean
}

internal class KtorEndpointHealthProbe(
    private val requests: Requests,
) : EndpointHealthProbe {
    override suspend fun isReachable(credentials: ServiceCredentials, timeoutMs: Long): Boolean =
        probeWith(timeoutMs) {
            requests.getOpenAICompatibleModels(Service.OpenAICompatible, credentials)
        }
}

/**
 * Shared bounded-probe semantics as a pure callable: `true` for a successful fetch, `false` for
 * any failure or timeout. A genuine [CancellationException] is rethrown so a stopped turn is
 * never mistaken for an outage. Tests exercise this directly with fakes; the only untestable
 * part of [KtorEndpointHealthProbe] is the delegation to [Requests].
 */
internal suspend fun probeWith(
    timeoutMs: Long,
    fetchModels: suspend () -> Result<*>,
): Boolean = try {
    withTimeout(timeoutMs) {
        fetchModels().isSuccess
    }
} catch (e: CancellationException) {
    if (e is TimeoutCancellationException) false else throw e
} catch (_: Exception) {
    false
}
