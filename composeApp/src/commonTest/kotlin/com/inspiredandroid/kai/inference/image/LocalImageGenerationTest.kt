package com.inspiredandroid.kai.inference.image

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalImageGenerationTest {
    private fun request(
        modelId: String = "sdxs-512-q4",
        modelPath: String = "/models/sdxs-512-q4.gguf",
        prompt: String = "a paper crane",
        steps: Int = 4,
        width: Int = 512,
        height: Int = 512,
    ) = ImageGenerationRequest(
        modelId = modelId,
        modelPath = modelPath,
        prompt = prompt,
        steps = steps,
        width = width,
        height = height,
    )

    @Test
    fun catalogContainsOnlySupportedShippingProfiles() {
        val shippingModels = IMAGE_GENERATION_MODELS.filterNot { it.benchmarkOnly }
        assertEquals(
            setOf(ImageModelKind.SDXS_512, ImageModelKind.SDXL_LIGHTNING, ImageModelKind.STEP_DISTILLED_SD_Q4),
            shippingModels.map { it.kind }.toSet(),
        )
        shippingModels.forEach {
            assertTrue(it.isEligibleForShipping)
            assertEquals(512, it.resolution)
            assertTrue(it.minimumSteps >= 1)
            assertTrue(it.maximumSteps <= 4)
            assertTrue(it.quantization.startsWith("Q4"))
        }
    }

    @Test
    fun fluxIsBenchmarkOnlyAndCannotShip() {
        val flux = IMAGE_GENERATION_MODELS.single { it.kind == ImageModelKind.FLUX_2_KLEIN_4B_Q4_K_M }
        assertTrue(flux.benchmarkOnly)
        assertFalse(flux.isEligibleForShipping)
        assertIs<ImageGenerationValidation.Invalid>(validateImageGenerationRequest(request(modelId = flux.id)))
    }

    @Test
    fun requestRequiresLocalModel512PixelsAndOneToFourSteps() {
        assertSame(ImageGenerationValidation.Valid, validateImageGenerationRequest(request(steps = 1)))
        assertSame(ImageGenerationValidation.Valid, validateImageGenerationRequest(request(steps = 4)))
        assertIs<ImageGenerationValidation.Invalid>(validateImageGenerationRequest(request(steps = 0)))
        assertIs<ImageGenerationValidation.Invalid>(validateImageGenerationRequest(request(steps = 5)))
        assertIs<ImageGenerationValidation.Invalid>(validateImageGenerationRequest(request(width = 768)))
        assertIs<ImageGenerationValidation.Invalid>(validateImageGenerationRequest(request(height = 768)))
        assertIs<ImageGenerationValidation.Invalid>(validateImageGenerationRequest(request(modelPath = "https://example.test/model.gguf")))
        assertIs<ImageGenerationValidation.Invalid>(validateImageGenerationRequest(request(prompt = " ")))
        assertIs<ImageGenerationValidation.Invalid>(validateImageGenerationRequest(request(modelId = "unknown")))
    }

    @Test
    fun backendResolutionUsesCapabilityGatesAndAvx2Fallback() {
        val desktop = setOf(ImageBackend.VULKAN_IGPU, ImageBackend.AVX2_CPU)
        assertEquals(ImageBackend.VULKAN_IGPU, resolveImageBackend(ImageBackend.VULKAN_IGPU, desktop))
        assertEquals(ImageBackend.AVX2_CPU, resolveImageBackend(ImageBackend.VULKAN_IGPU, desktop, setOf(ImageBackend.VULKAN_IGPU)))
        assertEquals(ImageBackend.AVX2_CPU, resolveImageBackend(ImageBackend.DIRECTML, desktop))
        assertNull(resolveImageBackend(ImageBackend.DIRECTML, setOf(ImageBackend.VULKAN_IGPU)))
        assertNull(resolveImageBackend(ImageBackend.VULKAN_IGPU, emptySet()))
    }

    @Test
    fun wrapperUnloadsImmediatelyAfterSuccess() = runTest {
        val engine = FakeEngine()
        val result = generateImageAndUnload(engine, request())
        assertEquals(ImageBackend.AVX2_CPU, result.backend)
        assertEquals(1, engine.unloadCalls)
        assertFalse(engine.loaded)
        engine.unload()
        assertEquals(2, engine.unloadCalls)
        assertFalse(engine.loaded)
    }

    @Test
    fun wrapperUnloadsImmediatelyAfterFailure() = runTest {
        val failure = IllegalStateException("native failure")
        val engine = FakeEngine(failure)
        val thrown = assertFailsWith<IllegalStateException> { generateImageAndUnload(engine, request()) }
        assertSame(failure, thrown)
        assertEquals(1, engine.unloadCalls)
        assertFalse(engine.loaded)
    }

    @Test
    fun wrapperUnloadsWhenValidationFailsBeforeGeneration() = runTest {
        val engine = FakeEngine()
        assertFailsWith<IllegalArgumentException> { generateImageAndUnload(engine, request(steps = 5)) }
        assertEquals(1, engine.unloadCalls)
        assertFalse(engine.loaded)
    }

    private class FakeEngine(
        private val failure: Throwable? = null,
    ) : LocalImageGenerationEngine {
        override val isAvailable = true
        override val supportedBackends = setOf(ImageBackend.AVX2_CPU)
        var loaded = false
        var unloadCalls = 0

        override suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult {
            loaded = true
            failure?.let { throw it }
            return ImageGenerationResult(byteArrayOf(1), ImageBackend.AVX2_CPU, 1L)
        }

        override fun unload() {
            unloadCalls++
            loaded = false
        }
    }
}
