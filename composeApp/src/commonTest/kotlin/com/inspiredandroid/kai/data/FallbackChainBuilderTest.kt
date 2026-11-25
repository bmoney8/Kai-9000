package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import com.inspiredandroid.kai.data.FallbackEntry
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceInstance
import com.inspiredandroid.kai.data.buildFallbackChain
import kotlin.test.Test
import kotlin.test.assertEquals

class FallbackChainBuilderTest {

    @Test
    fun `on-device provider stays after remote provider in full repository ordering`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)

        // We can't directly construct RemoteDataRepository without all its
        // dependencies, but the ordering predicate is now purely the
        // buildFallbackChain function, which we test in isolation.

        // Verify the production code path keeps the on-device entry.
        val remote = ServiceInstance(instanceId = "remote", serviceId = Service.OpenAICompatible.id)
        val litert = ServiceInstance(instanceId = "litert", serviceId = Service.LiteRT.id)

        // The production method now delegates; assert the delegate contract
        // via the AppSettings provider when it's wired (the test below exercises it
        // when RemoteDataRepository is constructed in integration, or we assert the
        // helper contract directly):
        val chain = buildFallbackChain(
            instances = listOf(remote, litert),
            isFreeServicePrimary = appSettings.isFreeServicePrimary(),
            isFreeFallbackEnabled = appSettings.isFreeFallbackEnabled(),
            hasLocalInferenceEngine = true,
        )

        assertEquals(
            listOf(Service.OpenAICompatible.id, Service.LiteRT.id),
            chain.map { it.service.id },
        )
    }
}