package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from `POST {base}/images/generations`.
 * Each entry in [data] carries an image either as base64 PNG or as a URL; gateways vary,
 * so both fields stay optional and default to null when absent.
 */
@Serializable
data class OpenAICompatibleImageResponseDto(
    val data: List<OpenAICompatibleImageData> = emptyList(),
    @SerialName("revised_prompt")
    val revisedPrompt: String? = null,
) {
    @Serializable
    data class OpenAICompatibleImageData(
        @SerialName("b64_json")
        val b64Json: String? = null,
        val url: String? = null,
        @SerialName("revised_prompt")
        val revisedPrompt: String? = null,
    )
}
