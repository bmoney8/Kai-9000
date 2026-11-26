package com.inspiredandroid.kai.inference.image

/** Backends supported by the local image-provider seam. No discrete-GPU backend is defined. */
enum class ImageBackend {
    VULKAN_IGPU,
    AVX2_CPU,
    DIRECTML,
}

/**
 * Resolves a preferred backend against platform-reported capabilities. [unavailable] can be used
 * after a backend initialization/generation failure to select the next supported option.
 * AVX2 is the guaranteed reference fallback, but is still checked in [available] so unsupported
 * platforms fail explicitly instead of attempting an unsafe native load.
 */
fun resolveImageBackend(
    preferred: ImageBackend,
    available: Set<ImageBackend>,
    unavailable: Set<ImageBackend> = emptySet(),
): ImageBackend? {
    val candidates = when (preferred) {
        ImageBackend.VULKAN_IGPU -> listOf(ImageBackend.VULKAN_IGPU, ImageBackend.AVX2_CPU)
        ImageBackend.DIRECTML -> listOf(ImageBackend.DIRECTML, ImageBackend.AVX2_CPU)
        ImageBackend.AVX2_CPU -> listOf(ImageBackend.AVX2_CPU)
    }
    return candidates.firstOrNull { it in available && it !in unavailable }
}

enum class ImageModelKind {
    SDXS_512,
    SDXL_LIGHTNING,
    STEP_DISTILLED_SD_Q4,
    FLUX_2_KLEIN_4B_Q4_K_M,
}

/** A local model profile; model bytes are user-managed and are not bundled or downloaded by Kai. */
data class ImageGenerationModel(
    val id: String,
    val displayName: String,
    val kind: ImageModelKind,
    val quantization: String,
    val resolution: Int = 512,
    val minimumSteps: Int = 1,
    val maximumSteps: Int = 4,
    val benchmarkOnly: Boolean = false,
) {
    val isEligibleForShipping: Boolean
        get() = !benchmarkOnly && resolution == 512 && quantization.isNotBlank() && minimumSteps in 1..4 && maximumSteps in minimumSteps..4
}

val IMAGE_GENERATION_MODELS = listOf(
    ImageGenerationModel(
        id = "sdxs-512-q4",
        displayName = "SDXS-512 (Q4)",
        kind = ImageModelKind.SDXS_512,
        quantization = "Q4",
    ),
    ImageGenerationModel(
        id = "sdxl-lightning-q4",
        displayName = "SDXL-Lightning (Q4)",
        kind = ImageModelKind.SDXL_LIGHTNING,
        quantization = "Q4",
    ),
    ImageGenerationModel(
        id = "step-distilled-sd-q4",
        displayName = "Step-distilled Stable Diffusion (Q4)",
        kind = ImageModelKind.STEP_DISTILLED_SD_Q4,
        quantization = "Q4",
    ),
    ImageGenerationModel(
        id = "flux-2-klein-4b-q4-k-m-benchmark",
        displayName = "FLUX.2-klein-4B Q4_K_M (benchmark only)",
        kind = ImageModelKind.FLUX_2_KLEIN_4B_Q4_K_M,
        quantization = "Q4_K_M",
        benchmarkOnly = true,
    ),
)

fun findImageGenerationModel(id: String): ImageGenerationModel? = IMAGE_GENERATION_MODELS.firstOrNull { it.id == id }

data class ImageGenerationRequest(
    val modelId: String,
    /** A user-selected local model file; it must never be a URL. */
    val modelPath: String,
    val prompt: String,
    val negativePrompt: String? = null,
    val seed: Long? = null,
    val steps: Int = 4,
    val width: Int = 512,
    val height: Int = 512,
    val preferredBackend: ImageBackend = ImageBackend.AVX2_CPU,
)

data class ImageGenerationResult(
    val imageBytes: ByteArray,
    val backend: ImageBackend,
    val elapsedMillis: Long,
)

sealed interface ImageGenerationValidation {
    data object Valid : ImageGenerationValidation
    data class Invalid(val reason: String) : ImageGenerationValidation
}

fun validateImageGenerationRequest(request: ImageGenerationRequest): ImageGenerationValidation {
    val model = findImageGenerationModel(request.modelId)
        ?: return ImageGenerationValidation.Invalid("Unknown local image model")
    if (!model.isEligibleForShipping) return ImageGenerationValidation.Invalid("Model is benchmark-only or outside the supported profile")
    if (request.modelPath.isBlank() || request.modelPath.contains("://")) {
        return ImageGenerationValidation.Invalid("A local model file path is required")
    }
    if (request.prompt.isBlank()) return ImageGenerationValidation.Invalid("Prompt must not be blank")
    if (request.width != 512 || request.height != 512 || model.resolution != 512) {
        return ImageGenerationValidation.Invalid("Only 512x512 generation is supported")
    }
    if (request.steps !in model.minimumSteps..model.maximumSteps) {
        return ImageGenerationValidation.Invalid("Step count must be between ${model.minimumSteps} and ${model.maximumSteps}")
    }
    return ImageGenerationValidation.Valid
}

/**
 * Local image-runtime boundary. Implementations must not retain runtime/model allocations after
 * [unload] returns. The provider wrapper below guarantees unload after both success and failure.
 */
interface LocalImageGenerationEngine {
    val isAvailable: Boolean
    val supportedBackends: Set<ImageBackend>

    suspend fun generate(request: ImageGenerationRequest): ImageGenerationResult

    /** Synchronous, immediate, idempotent release of native runtime and model resources. */
    fun unload()
}

/** Runs one image request and releases the image runtime in the same call, including on failure. */
suspend fun generateImageAndUnload(
    engine: LocalImageGenerationEngine,
    request: ImageGenerationRequest,
): ImageGenerationResult {
    return try {
        require(engine.isAvailable) { "Local image generation is not available on this platform" }
        when (val validation = validateImageGenerationRequest(request)) {
            ImageGenerationValidation.Valid -> Unit
            is ImageGenerationValidation.Invalid -> throw IllegalArgumentException(validation.reason)
        }
        engine.generate(request)
    } finally {
        engine.unload()
    }
}
