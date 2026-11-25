package com.inspiredandroid.kai.inference

/** Fixed allowance for runtime, allocator fragmentation, and other concurrent app work. */
const val MODEL_LOAD_SAFETY_MARGIN_BYTES: Long = 512L * 1024 * 1024

/**
 * Estimates memory needed to load and run a model: resident model weights, configured
 * accelerator allocation, context/KV working set, and a fixed safety margin.
 */
fun requiredModelMemoryBytes(
    modelBytes: Long,
    acceleratorMemoryMb: Int,
    contextTokens: Int,
    kvBytesPerToken: Int,
    safetyMarginBytes: Long = MODEL_LOAD_SAFETY_MARGIN_BYTES,
): Long {
    fun saturatedAdd(left: Long, right: Long): Long =
        if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    fun saturatedMultiply(left: Long, right: Long): Long = when {
        left == 0L || right == 0L -> 0L
        left > 0L && right > 0L && left > Long.MAX_VALUE / right -> Long.MAX_VALUE
        else -> left * right
    }

    val model = modelBytes.coerceAtLeast(0L)
    val accelerator = saturatedMultiply(acceleratorMemoryMb.coerceAtLeast(0).toLong(), 1024L * 1024L)
    val context = saturatedMultiply(contextTokens.coerceAtLeast(0).toLong(), kvBytesPerToken.coerceAtLeast(0).toLong())
    return saturatedAdd(saturatedAdd(saturatedAdd(model, accelerator), context), safetyMarginBytes.coerceAtLeast(0L))
}

/** Unknown memory is the only case in which the guard deliberately allows loading. */
fun hasEnoughMemory(availableBytes: Long, requiredBytes: Long): Boolean =
    availableBytes == Long.MAX_VALUE || (availableBytes >= 0L && availableBytes >= requiredBytes)
