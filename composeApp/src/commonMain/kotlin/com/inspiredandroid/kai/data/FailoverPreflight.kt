package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.ServiceCredentials

/** Where a turn's request is routed after a successful or failed preflight check. */
internal enum class FailoverRoute {
    REMOTE,
    LOCAL_TEXT,
    LOCAL_TEXT_AND_IMAGE,
}

/**
 * The routing decision shown in the same status line as provider fallbacks.
 *
 * This fork has no `X-OmniRoute-Decision` / `X-OmniRoute-Compression` response headers to read;
 * the decision is derived locally and displayed in the existing fallback status UI instead.
 */
internal data class RoutingDecision(
    val route: FailoverRoute,
    val failedServiceName: String,
    val selectedServiceName: String,
) {
    val tooltipText: String
        get() = when (route) {
            FailoverRoute.REMOTE -> ""
            FailoverRoute.LOCAL_TEXT ->
                "$failedServiceName unreachable → $selectedServiceName (text)"
            FailoverRoute.LOCAL_TEXT_AND_IMAGE ->
                "$failedServiceName unreachable → $selectedServiceName (text + image)"
        }
}

/** Pick the offline path. Image failover is only real once a local image engine is supplied. */
internal fun selectFailoverRoute(
    hasLocalText: Boolean,
    hasLocalImage: Boolean,
): FailoverRoute? = when {
    hasLocalText && hasLocalImage -> FailoverRoute.LOCAL_TEXT_AND_IMAGE
    hasLocalText -> FailoverRoute.LOCAL_TEXT
    else -> null
}

internal data class FailoverPreflightResult<T>(
    val entries: List<T>,
    val decision: RoutingDecision? = null,
)

/**
 * Pre-fallback health check for an OpenAI-compatible (OmniRoute / mesh) primary.
 *
 * When the primary endpoint is unreachable, remote entries before the first on-device entry are
 * skipped for this turn only. The original ordered chain is untouched for later turns, so the
 * failover is reversible and a recovered endpoint is probed again automatically. Failed turns
 * are still handled by [FallbackTurnTraversal]; this class never changes its cancellation or
 * retry semantics.
 */
internal class FailoverPreflight(
    private val isEnabled: () -> Boolean,
    private val timeoutMs: () -> Long,
    private val probe: EndpointHealthProbe,
    private val hasLocalImageCapability: () -> Boolean = { false },
) {
    suspend fun <T> apply(
        entries: List<T>,
        serviceOf: (T) -> Service,
        credentialsOf: (T) -> ServiceCredentials,
    ): FailoverPreflightResult<T> {
        if (!isEnabled()) return FailoverPreflightResult(entries)
        val primary = entries.firstOrNull() ?: return FailoverPreflightResult(entries)
        if (serviceOf(primary) != Service.OpenAICompatible) return FailoverPreflightResult(entries)
        val localIndex = entries.indexOfFirst { serviceOf(it).isOnDevice }
        if (localIndex <= 0) return FailoverPreflightResult(entries)
        val route = selectFailoverRoute(
            hasLocalText = true,
            hasLocalImage = hasLocalImageCapability(),
        ) ?: return FailoverPreflightResult(entries)

        val reachable = probe.isReachable(
            credentials = credentialsOf(primary),
            timeoutMs = timeoutMs().coerceAtLeast(1L),
        )
        if (reachable) return FailoverPreflightResult(entries)

        return FailoverPreflightResult(
            entries = entries.drop(localIndex),
            decision = RoutingDecision(
                route = route,
                failedServiceName = serviceOf(primary).displayName,
                selectedServiceName = serviceOf(entries[localIndex]).displayName,
            ),
        )
    }
}
