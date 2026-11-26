package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Locks the wire shape of the new `/images/generations` DTOs. The chat request shape is
 * deliberately untouched; this is a separate payload.
 */
class OpenAICompatibleImageDtoTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `request uses the OpenAI image generation field names`() {
        val body = json.parseToJsonElement(
            json.encodeToString(
                OpenAICompatibleImageRequestDto.serializer(),
                OpenAICompatibleImageRequestDto(
                    prompt = "a lantern on a desk",
                    model = "flux1-schnell",
                    n = 1,
                    size = "1024x1024",
                    responseFormat = "b64_json",
                ),
            ),
        ) as JsonObject
        assertEquals("a lantern on a desk", body["prompt"]?.jsonPrimitive?.content)
        assertEquals("flux1-schnell", body["model"]?.jsonPrimitive?.content)
        assertEquals("b64_json", body["response_format"]?.jsonPrimitive?.content)
        assertEquals("1024x1024", body["size"]?.jsonPrimitive?.content)
    }

    @Test
    fun `response parses both b64 and url image shapes`() {
        val dto = json.decodeFromString<OpenAICompatibleImageResponseDto>(
            """{"data":[{"b64_json":"AAAA","revised_prompt":"revised"},{"url":"https://x/image.png"}]}""",
        )
        assertEquals(2, dto.data.size)
        assertEquals("AAAA", dto.data[0].b64Json)
        assertEquals("revised", dto.data[0].revisedPrompt)
        assertNull(dto.data[0].url)
        assertEquals("https://x/image.png", dto.data[1].url)
        assertNull(dto.data[1].b64Json)
    }
}
