package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** A pasted full chat endpoint must not end up as `/chat/completions/chat/completions` (#516). */
class OpenAICompatibleBaseUrlTest {

    private fun normalize(url: String) = Service.normalizeOpenAICompatibleBaseUrl(url)

    @Test
    fun plainBaseUrlIsUnchanged() {
        assertEquals("https://api.example.com/v1", normalize("https://api.example.com/v1"))
        assertEquals("https://api.example.com/v1", normalize("https://api.example.com/v1/"))
    }

    @Test
    fun pastedChatEndpointIsStrippedToTheBase() {
        assertEquals("https://api.example.com/v1", normalize("https://api.example.com/v1/chat/completions"))
        assertEquals("https://api.example.com/v1", normalize(" https://api.example.com/v1/chat/completions/ "))
        assertEquals("https://api.example.com/v1", normalize("https://api.example.com/v1/Chat/Completions"))
    }

    @Test
    fun blankFallsBackToTheDefault() {
        assertEquals(Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL, normalize(""))
    }

    @Test
    fun imageEndpointResolvesAgainstTheSameNormalizedBase() {
        val imagePath = Service.OpenAICompatible.imageEndpoint!!
        assertEquals(
            "https://api.example.com/v1/images/generations",
            Service.resolveOpenAICompatibleEndpoint("https://api.example.com/v1", imagePath),
        )
        // A pasted full chat endpoint must not leak into the images path.
        assertEquals(
            "https://api.example.com/v1/images/generations",
            Service.resolveOpenAICompatibleEndpoint("https://api.example.com/v1/chat/completions", imagePath),
        )
        assertEquals(
            "${Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL}/images/generations",
            Service.resolveOpenAICompatibleEndpoint("", imagePath),
        )
    }

    @Test
    fun chatEndpointResolutionIsUnchanged() {
        // Guardrail: chat URLs must stay byte-for-byte identical.
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            Service.resolveOpenAICompatibleEndpoint("https://api.example.com/v1", Service.OpenAICompatible.chatUrl),
        )
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            Service.resolveOpenAICompatibleEndpoint("https://api.example.com/v1/chat/completions", Service.OpenAICompatible.chatUrl),
        )
    }
}
