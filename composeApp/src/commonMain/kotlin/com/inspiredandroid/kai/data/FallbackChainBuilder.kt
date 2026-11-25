package com.inspiredandroid.kai.data

internal data class FallbackEntry(val instanceId: String, val service: Service)

/**
 * Build the fallback chain in configured order.
 *
 * On-device entries are only eligible when the local inference engine is available;
 * otherwise they are filtered out before ordering. Once eligible, they remain in
 * their configured position so they can serve as a fallback (for example,
 * Omniroute first and LiteRT second).
 */
internal fun buildFallbackChain(
    instances: List<ServiceInstance>,
    isFreeServicePrimary: Boolean,
    isFreeFallbackEnabled: Boolean,
    hasLocalInferenceEngine: Boolean,
): List<FallbackEntry> {
    val entries = instances.map { FallbackEntry(instanceId = it.instanceId, service = Service.fromId(it.serviceId)) }
        .filter { it.service != Service.Free }
        .filter { !it.service.isOnDevice || hasLocalInferenceEngine }
    val freeEntry = FallbackEntry(instanceId = "free", service = Service.Free)
    return if (entries.isEmpty()) {
        listOf(freeEntry)
    } else if (isFreeServicePrimary) {
        listOf(freeEntry) + entries
    } else if (isFreeFallbackEnabled) {
        entries + freeEntry
    } else {
        entries
    }
}
