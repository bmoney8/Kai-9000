package com.inspiredandroid.kai.network

import kotlinx.serialization.Serializable

/**
 * Subtle routing metadata from a gateway that chooses between local, mesh, and fallback models.
 * Both fields are nullable: gateways that do not emit these headers (plain OpenAI, Ollama,
 * LiteLLM) leave both null, which callers render as nothing.
 */
@Serializable
data class OmniRouteRouting(
    val decision: String? = null,
    val compression: String? = null,
) {
    val isEmpty: Boolean get() = decision == null && compression == null
}

/**
 * Parses `X-OmniRoute-Decision` and `X-OmniRoute-Compression` from HTTP response headers.
 * Header names are case-insensitive. Blank, control-character-only, and absurdly long values are
 * treated as absent — they are metadata for a tooltip, not data used for routing decisions.
 */
fun parseOmniRouteHeaders(headers: Map<String, String>?): OmniRouteRouting? {
    if (headers.isNullOrEmpty()) return null
    fun read(name: String): String? {
        val value = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
            ?.trim()
            ?: return null
        if (value.isEmpty() || value.length > MAX_HEADER_VALUE_LENGTH) return null
        if (value.any { it.isISOControl() }) return null
        return value
    }
    val decision = read(HEADER_DECISION)
    val compression = read(HEADER_COMPRESSION)
    return if (decision == null && compression == null) null else OmniRouteRouting(decision, compression)
}

private const val HEADER_DECISION = "X-OmniRoute-Decision"
private const val HEADER_COMPRESSION = "X-OmniRoute-Compression"
private const val MAX_HEADER_VALUE_LENGTH = 120
