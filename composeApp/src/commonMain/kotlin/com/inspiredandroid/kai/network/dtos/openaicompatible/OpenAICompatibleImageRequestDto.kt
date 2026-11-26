package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request payload for `POST {base}/images/generations`.
 * Deliberately its own DTO: the chat request shape is untouched.
 */
@Serializable
data class OpenAICompatibleImageRequestDto(
    val prompt: String,
    val model: String,
    /** Number of images to generate. Null keeps the provider default. */
    val n: Int? = null,
    /** Size, e.g. "1024x1024". Null keeps the provider default. */
    val size: String? = null,
    /** "url" or "b64_json" when the provider distinguishes them. */
    @SerialName("response_format")
    val responseFormat: String? = null,
)
