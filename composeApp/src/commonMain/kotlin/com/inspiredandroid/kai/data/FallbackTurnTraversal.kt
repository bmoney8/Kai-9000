package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.AllServicesFailedException
import com.inspiredandroid.kai.network.OpenAICompatibleEmptyResponseException
import kotlinx.coroutines.CancellationException

/**
 * One configured provider in a chat turn's fallback chain.
 *
 * Production wires [respond] to `RemoteDataRepository.askWithService`; tests supply a fake
 * that never touches the network, so the same traversal code can be exercised deterministically.
 */
internal interface FallbackTurnProvider<T> {
    val entry: FallbackEntry
    suspend fun respond(): T
}

/** The successful turn, together with the provider that answered it. */
internal data class FallbackTurnResult<T>(
    val turn: T,
    val entry: FallbackEntry,
    val isFallback: Boolean,
)

/**
 * Walks the configured fallback chain for a single chat turn.
 *
 * A failed remote provider is reported through [onFallback] and the next provider is tried.
 * Cancellation and on-device failures are surfaced immediately rather than being mistaken for
 * a provider outage. The successful turn is handed to [onSuccess] exactly once so callers can
 * append it to history and persist the conversation; [onDone] always clears any transient
 * fallback status, including on cancellation or failure.
 */
internal class FallbackTurnTraversal<T>(
    private val providers: List<FallbackTurnProvider<T>>,
    private val onFallback: suspend (
        failed: FallbackTurnProvider<T>,
        error: Exception,
        next: FallbackTurnProvider<T>?,
    ) -> Unit,
    private val onSuccess: suspend (FallbackTurnResult<T>) -> Unit,
    private val onDone: suspend () -> Unit,
) {
    suspend fun run(): FallbackTurnResult<T> {
        var lastException: Exception? = null
        try {
            for ((index, provider) in providers.withIndex()) {
                val turn = try {
                    provider.respond()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // On-device services should not silently fall back — surface the error.
                    if (provider.entry.service.isOnDevice) throw e
                    lastException = e
                    onFallback(provider, e, providers.getOrNull(index + 1))
                    continue
                }
                val result = FallbackTurnResult(
                    turn = turn,
                    entry = provider.entry,
                    isFallback = index > 0,
                )
                onSuccess(result)
                return result
            }

            throw if (providers.size > 1 && lastException != null) {
                AllServicesFailedException()
            } else {
                lastException ?: OpenAICompatibleEmptyResponseException()
            }
        } finally {
            onDone()
        }
    }
}
