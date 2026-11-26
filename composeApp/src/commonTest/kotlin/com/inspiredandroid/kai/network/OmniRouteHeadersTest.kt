package com.inspiredandroid.kai.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmniRouteHeadersTest {

    @Test
    fun `present headers are parsed case-insensitively`() {
        val routing = parseOmniRouteHeaders(
            mapOf(
                "x-omniroute-decision" to "local",
                "X-OMNIROUTE-COMPRESSION" to "zstd",
            ),
        )
        assertEquals("local", routing?.decision)
        assertEquals("zstd", routing?.compression)
        assertTrue(routing?.isEmpty == false)
    }

    @Test
    fun `absent headers return null`() {
        assertNull(parseOmniRouteHeaders(null))
        assertNull(parseOmniRouteHeaders(emptyMap()))
        assertNull(parseOmniRouteHeaders(mapOf("Content-Type" to "application/json")))
    }

    @Test
    fun `blank and malformed header values are ignored`() {
        val routing = parseOmniRouteHeaders(
            mapOf(
                "X-OmniRoute-Decision" to "  \t ",
                "X-OmniRoute-Compression" to "x".repeat(1_000),
            ),
        )
        assertNull(routing)
    }

    @Test
    fun `only one present header is still useful`() {
        val routing = parseOmniRouteHeaders(mapOf("X-OmniRoute-Decision" to "mesh"))
        assertEquals("mesh", routing?.decision)
        assertNull(routing?.compression)

        val other = parseOmniRouteHeaders(mapOf("X-OmniRoute-Compression" to "none"))
        assertNull(other?.decision)
        assertEquals("none", other?.compression)
    }
}
