package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals

class FallbackChainBuilderTest {

    private val remote = ServiceInstance(instanceId = "remote", serviceId = Service.OpenAICompatible.id)
    private val onDevice = ServiceInstance(instanceId = "litert", serviceId = Service.LiteRT.id)

    @Test
    fun `on-device provider stays in fallback chain after remote provider`() {
        val chain = buildFallbackChain(
            instances = listOf(remote, onDevice),
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
    fun `on-device provider is excluded when local inference engine is unavailable`() {
        val chain = buildFallbackChain(
            instances = listOf(remote, onDevice),
            isFreeServicePrimary = false,
            isFreeFallbackEnabled = false,
            hasLocalInferenceEngine = false,
        )

        assertEquals(listOf(Service.OpenAICompatible.id), chain.map { it.service.id })
    }

    @Test
    fun `free tier is appended after on-device fallback when enabled`() {
        val chain = buildFallbackChain(
            instances = listOf(remote, onDevice),
            isFreeServicePrimary = false,
            isFreeFallbackEnabled = true,
            hasLocalInferenceEngine = true,
        )

        assertEquals(
            listOf(Service.OpenAICompatible.id, Service.LiteRT.id, Service.Free.id),
            chain.map { it.service.id },
        )
    }
}
