/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import org.jetbrains.ai.tracy.core.instrument
import org.jetbrains.ai.tracy.openai.adapters.OpenAILLMTracingAdapter
import org.jetbrains.ai.tracy.test.utils.BaseAITracingTest
import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag("openai")
class OpenAICoverageAttributesTest : BaseAITracingTest() {
    @Test
    fun `common OpenAI attributes are emitted for resource endpoints`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"object":"list","data":[{"id":"model-1","object":"model"}]}""")
            )

            val client = instrument(OkHttpClient(), OpenAILLMTracingAdapter())
            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/models"))
                    .get()
                    .build()
            ).execute().use { it.body.string() }

            val trace = analyzeSpans().single()
            assertEquals("openai", trace.attributes[AttributeKey.stringKey("gen_ai.provider.name")])
            assertEquals("models.list", trace.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("models", trace.attributes[AttributeKey.stringKey("openai.api.type")])
            assertEquals(200, trace.attributes[AttributeKey.longKey("http.response.status_code")])
            assertNotNull(trace.attributes[AttributeKey.stringKey("server.address")])
            assertNotNull(trace.attributes[AttributeKey.longKey("server.port")])
            assertEquals("list", trace.attributes[AttributeKey.stringKey("tracy.response.object")])
        }
    }

    @Test
    fun `audio transcription multipart attributes are traced`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"text":"BEEP","duration":0.5,"language":"english","words":[{"word":"BEEP"}]}""")
            )

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("timestamp_granularities[]", "word")
                .addFormDataPart(
                    "file",
                    "lofi.wav",
                    "RIFF....WAVE".toRequestBody("audio/wav".toMediaType())
                )
                .build()

            val client = instrument(OkHttpClient(), OpenAILLMTracingAdapter())
            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/audio/transcriptions"))
                    .post(body)
                    .build()
            ).execute().use { it.body.string() }

            val trace = analyzeSpans().single()
            assertEquals("audio.transcription", trace.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("audio", trace.attributes[AttributeKey.stringKey("openai.api.type")])
            assertEquals("json", trace.attributes[AttributeKey.stringKey("gen_ai.output.type")])
            assertEquals("whisper-1", trace.attributes[AttributeKey.stringKey("gen_ai.request.model")])
            assertEquals("verbose_json", trace.attributes[AttributeKey.stringKey("tracy.request.response_format")])
            assertEquals("word", trace.attributes[AttributeKey.stringKey("tracy.request.timestamp_granularities")])
            assertEquals("wav", trace.attributes[AttributeKey.stringKey("tracy.request.audio.format")])
            assertEquals(12, trace.attributes[AttributeKey.longKey("tracy.request.audio.size_bytes")])
            assertEquals(0.5, trace.attributes[AttributeKey.doubleKey("tracy.response.transcription.duration_seconds")])
            assertEquals(1, trace.attributes[AttributeKey.longKey("tracy.response.transcription.words.count")])
        }
    }
}
