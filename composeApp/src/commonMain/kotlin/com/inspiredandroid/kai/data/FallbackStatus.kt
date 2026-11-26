package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.UiError

/**
 * Emitted while [DataRepository.ask] walks the configured fallback chain: when a service has
 * just failed and the loop is moving on, or when a preflight health check skipped an unreachable
 * primary. The UI keeps showing the status while the next service is being tried, so neither
 * kind of fallback is silent.
 */
data class FallbackStatus(
    val serviceName: String,
    val errorReason: UiError,
    val nextServiceName: String? = null,
    /** Set when the turn skipped a remote primary after its preflight health check failed. */
    val routingDecision: String? = null,
)

/**
 * Text for the thinking-row status tooltip. A preflight decision takes precedence because the
 * skipped primary was never called — there is no per-attempt error to report for it.
 */
internal fun FallbackStatus.tooltipText(failedText: String, tryingNextText: String?): String =
    routingDecision ?: if (tryingNextText != null) "$failedText\n$tryingNextText" else failedText
