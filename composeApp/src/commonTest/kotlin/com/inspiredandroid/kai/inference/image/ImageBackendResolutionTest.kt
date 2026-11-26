package com.inspiredandroid.kai.inference.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageBackendResolutionTest {
    @Test
    fun prefersVulkanWhenIntegratedGpuCapabilityIsAdvertised() {
        val available = setOf(ImageBackend.VULKAN_IGPU, ImageBackend.AVX2_CPU)

        assertEquals(
            ImageBackend.VULKAN_IGPU,
            resolveImageBackend(ImageBackend.VULKAN_IGPU, available),
        )
    }

    @Test
    fun fallsBackToAvx2WhenVulkanIsMissingOrFails() {
        assertEquals(
            ImageBackend.AVX2_CPU,
            resolveImageBackend(ImageBackend.VULKAN_IGPU, setOf(ImageBackend.AVX2_CPU)),
        )
        assertEquals(
            ImageBackend.AVX2_CPU,
            resolveImageBackend(
                ImageBackend.VULKAN_IGPU,
                setOf(ImageBackend.VULKAN_IGPU, ImageBackend.AVX2_CPU),
                unavailable = setOf(ImageBackend.VULKAN_IGPU),
            ),
        )
    }

    @Test
    fun directMlIsSelectedOnlyWhenAdvertisedAndOtherwiseUsesAvx2() {
        assertEquals(
            ImageBackend.DIRECTML,
            resolveImageBackend(
                ImageBackend.DIRECTML,
                setOf(ImageBackend.DIRECTML, ImageBackend.AVX2_CPU),
            ),
        )
        assertEquals(
            ImageBackend.AVX2_CPU,
            resolveImageBackend(ImageBackend.DIRECTML, setOf(ImageBackend.AVX2_CPU)),
        )
        assertNull(resolveImageBackend(ImageBackend.DIRECTML, setOf(ImageBackend.VULKAN_IGPU)))
    }

    @Test
    fun returnsNoBackendWhenNoSafeCapabilityIsAvailable() {
        assertNull(resolveImageBackend(ImageBackend.AVX2_CPU, emptySet()))
        assertNull(resolveImageBackend(ImageBackend.VULKAN_IGPU, emptySet()))
    }
}
