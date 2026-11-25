package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import com.inspiredandroid.kai.data.FallbackEntry
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceInstance
import com.inspiredandroid.kai.data.buildFallbackChain
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteDataRepositoryFallbackTest {

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
}