/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters.handlers

import io.opentelemetry.api.common.AttributeKey
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import org.jetbrains.ai.tracy.core.instrument
import org.jetbrains.ai.tracy.openai.adapters.BaseOpenAITracingTest
import org.jetbrains.ai.tracy.openai.adapters.OpenAILLMTracingAdapter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GenericOpenAIApiEndpointHandlersTest : BaseOpenAITracingTest() {
    @Test
    fun `common semantic and HTTP attributes are emitted`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "id": "chatcmpl_123",
                          "object": "chat.completion",
                          "model": "gpt-4o-mini",
                          "choices": [{"index": 0, "finish_reason": "stop", "message": {"role": "assistant", "content": "ok"}}],
                          "usage": {"prompt_tokens": 1, "completion_tokens": 1}
                        }
                        """.trimIndent()
                    )
            )

            val client = OkHttpClient().instrumented()
            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/chat/completions"))
                    .post(
                        """
                        {"model":"gpt-4o-mini","messages":[{"role":"user","content":"hi"}]}
                        """.trimIndent().toRequestBody("application/json".toMediaType())
                    )
                    .build()
            ).execute().close()

            val span = analyzeSpans().single()
            assertEquals("openai", span.attributes[AttributeKey.stringKey("gen_ai.provider.name")])
            assertEquals("chat", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("chat_completions", span.attributes[AttributeKey.stringKey("openai.api.type")])
            assertEquals(200L, span.attributes[AttributeKey.longKey("http.response.status_code")])
            assertEquals(server.hostName, span.attributes[AttributeKey.stringKey("server.address")])
            assertNotNull(span.attributes[AttributeKey.longKey("server.port")])
            assertEquals("stop", span.attributes[AttributeKey.stringKey("gen_ai.response.finish_reasons")])
        }
    }

    @Test
    fun `audio speech sse stream is traced`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setResponseCode(200)
                    .setBody(
                        """
                        data: {"delta":"hello"}

                        data: {"delta":" world"}

                        """.trimIndent()
                    )
            )

            OkHttpClient().instrumented().newCall(
                Request.Builder()
                    .url(server.url("/v1/audio/speech"))
                    .post(
                        """
                        {"model":"gpt-4o-mini-tts","voice":"alloy","input":"hello","stream_format":"sse"}
                        """.trimIndent().toRequestBody("application/json".toMediaType())
                    )
                    .build()
            ).execute().use { response ->
                response.body?.string()
            }

            val span = analyzeSpans().single()
            assertEquals("audio.speech", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals(true, span.attributes[AttributeKey.booleanKey("gen_ai.response.streaming")])
            assertEquals(2L, span.attributes[AttributeKey.longKey("tracy.response.stream.events.count")])
            assertEquals("sse", span.attributes[AttributeKey.stringKey("tracy.request.stream_format")])
        }
    }

    @Test
    fun `audio transcription multipart request and response are traced`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "text": "BEEP",
                          "task": "transcribe",
                          "language": "english",
                          "duration": 0.5,
                          "words": [{"word": "BEEP", "start": 0.0, "end": 0.5}],
                          "usage": {"input_tokens": 2, "output_tokens": 1}
                        }
                        """.trimIndent()
                    )
            )

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("prompt", "context")
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("timestamp_granularities[]", "word")
                .addFormDataPart(
                    "file",
                    "beep.wav",
                    "RIFF....WAVE".toRequestBody("audio/wav".toMediaType())
                )
                .build()

            OkHttpClient().instrumented().newCall(
                Request.Builder()
                    .url(server.url("/v1/audio/transcriptions"))
                    .post(body)
                    .build()
            ).execute().close()

            val span = analyzeSpans().single()
            assertEquals("audio.transcription", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("audio", span.attributes[AttributeKey.stringKey("openai.api.type")])
            assertEquals("whisper-1", span.attributes[AttributeKey.stringKey("gen_ai.request.model")])
            assertEquals("verbose_json", span.attributes[AttributeKey.stringKey("tracy.request.response_format")])
            assertEquals(true, span.attributes[AttributeKey.booleanKey("tracy.request.prompt.present")])
            assertEquals("word", span.attributes[AttributeKey.stringKey("tracy.request.timestamp_granularities")])
            assertEquals(12L, span.attributes[AttributeKey.longKey("tracy.request.audio.size_bytes")])
            assertEquals("wav", span.attributes[AttributeKey.stringKey("tracy.request.audio.format")])
            assertEquals(0.5, span.attributes[AttributeKey.doubleKey("tracy.response.transcription.duration_seconds")])
            assertEquals(1L, span.attributes[AttributeKey.longKey("tracy.response.transcription.words.count")])
        }
    }

    @Test
    fun `batch list pagination response is traced`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "object": "list",
                          "data": [{"id": "batch_1", "object": "batch", "status": "completed"}],
                          "first_id": "batch_1",
                          "last_id": "batch_1",
                          "has_more": false
                        }
                        """.trimIndent()
                    )
            )

            OkHttpClient().instrumented().newCall(
                Request.Builder()
                    .url(server.url("/v1/batches?limit=1&after=batch_0"))
                    .get()
                    .build()
            ).execute().close()

            val span = analyzeSpans().single()
            assertEquals("batches.list", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("batches", span.attributes[AttributeKey.stringKey("openai.api.type")])
            assertEquals(1L, span.attributes[AttributeKey.longKey("tracy.request.limit")])
            assertEquals("batch_0", span.attributes[AttributeKey.stringKey("tracy.request.after")])
            assertEquals("list", span.attributes[AttributeKey.stringKey("tracy.response.object")])
            assertEquals(1L, span.attributes[AttributeKey.longKey("tracy.response.list.count")])
            assertEquals(false, span.attributes[AttributeKey.booleanKey("tracy.response.has_more")])
        }
    }

    @Test
    fun `image variation multipart request and response are traced`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "created": 1713833628,
                          "data": [{"url": "https://example.test/image.png"}]
                        }
                        """.trimIndent()
                    )
            )

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "gpt-image-1")
                .addFormDataPart("n", "1")
                .addFormDataPart(
                    "image",
                    "input.png",
                    "png-bytes".toRequestBody("image/png".toMediaType())
                )
                .build()

            OkHttpClient().instrumented().newCall(
                Request.Builder()
                    .url(server.url("/v1/images/variations"))
                    .post(body)
                    .build()
            ).execute().close()

            val span = analyzeSpans().single()
            assertEquals("generate_content", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("images", span.attributes[AttributeKey.stringKey("openai.api.type")])
            assertEquals("image", span.attributes[AttributeKey.stringKey("gen_ai.output.type")])
            assertEquals("gpt-image-1", span.attributes[AttributeKey.stringKey("gen_ai.request.model")])
            assertEquals(9L, span.attributes[AttributeKey.longKey("tracy.request.image.size_bytes")])
            assertEquals(1713833628L, span.attributes[AttributeKey.longKey("tracy.response.created")])
            assertNotNull(span.attributes[AttributeKey.stringKey("tracy.response.image.url")])
        }
    }

    private fun OkHttpClient.instrumented(): OkHttpClient = instrument(this, OpenAILLMTracingAdapter())
}
