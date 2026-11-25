package com.inspiredandroid.kai.inference

/** Fixed allowance for runtime, allocator fragmentation, and other concurrent app work. */
const val MODEL_LOAD_SAFETY_MARGIN_BYTES: Long = 512L * 1024 * 1024

/** Normalize a platform memory reading, preserving the unbounded sentinel only for invalid data. */
fun finiteMemoryOrUnknown(value: Long, zeroIsValid: Boolean = false): Long =
    value.takeIf { if (zeroIsValid) it >= 0L else it > 0L } ?: Long.MAX_VALUE

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

fun hasEnoughMemory(availableBytes: Long, requiredBytes: Long): Boolean =
    availableBytes == Long.MAX_VALUE || (availableBytes >= 0L && availableBytes >= requiredBytes)

/** JVM-shared Android/desktop allocation seam; the callback is not invoked on rejection. */
inline fun <T> loadJvmEngineWithMemoryGuard(availableBytes: Long, requiredBytes: Long, load: () -> T): T {
    if (!hasEnoughMemory(availableBytes, requiredBytes)) throw InsufficientMemoryException()
    return load()
}

/** iOS bridge seam; rejects before native allocation begins. */
inline fun <T> loadIosBridgeWithMemoryGuard(availableBytes: Long, requiredBytes: Long, load: () -> T): T {
    if (!hasEnoughMemory(availableBytes, requiredBytes)) throw InsufficientMemoryException()
    return load()
}

fun iosAvailableMemoryBytes(rawValue: Long): Long = finiteMemoryOrUnknown(rawValue)

fun desktopAvailableMemoryBytes(value: Long?): Long =
    finiteMemoryOrUnknown(value ?: -1L, zeroIsValid = true)

fun desktopTotalMemoryBytes(value: Long?): Long = finiteMemoryOrUnknown(value ?: -1L)
