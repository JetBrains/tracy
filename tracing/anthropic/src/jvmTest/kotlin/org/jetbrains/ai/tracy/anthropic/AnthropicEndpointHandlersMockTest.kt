/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jetbrains.ai.tracy.anthropic.adapters.AnthropicLLMTracingAdapter
import org.jetbrains.ai.tracy.core.instrument
import org.jetbrains.ai.tracy.test.utils.BaseAITracingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [AnthropicLLMTracingAdapter] endpoint routing using [MockWebServer].
 *
 * These tests do not call the real Anthropic API and do not require an API key.
 */
class AnthropicEndpointHandlersMockTest : BaseAITracingTest() {

    private fun instrumentedClient(server: MockWebServer): OkHttpClient =
        instrument(OkHttpClient(), AnthropicLLMTracingAdapter())

    private fun MockWebServer.jsonResponse(body: String, code: Int = 200): MockWebServer {
        enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
        return this
    }

    // ─── Messages ─────────────────────────────────────────────────────────────

    @Test
    fun `messages request sets api type and operation name`() = runTest {
        withMockServer { server ->
            server.jsonResponse(MESSAGES_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/messages"))
                    .post(MESSAGES_REQUEST_BODY.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("messages", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("chat", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("claude-haiku-4-5", span.attributes[AttributeKey.stringKey("gen_ai.request.model")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.id")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.model")])
            assertEquals(200L, span.attributes[AttributeKey.longKey("http.response.status_code")])
            assertNotNull(span.attributes[AttributeKey.stringKey("server.address")])
            assertNotNull(span.attributes[AttributeKey.longKey("server.port")])
            assertEquals("anthropic", span.attributes[AttributeKey.stringKey("gen_ai.system")])
        }
    }

    // ─── Count tokens ──────────────────────────────────────────────────────────

    @Test
    fun `count_tokens request sets api type, operation name and usage`() = runTest {
        withMockServer { server ->
            server.jsonResponse(COUNT_TOKENS_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/messages/count_tokens"))
                    .post(COUNT_TOKENS_REQUEST_BODY.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("count_tokens", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("count_tokens", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("claude-haiku-4-5", span.attributes[AttributeKey.stringKey("gen_ai.request.model")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.usage.input_tokens")])
            assertEquals(200L, span.attributes[AttributeKey.longKey("http.response.status_code")])
        }
    }

    // ─── Batches ───────────────────────────────────────────────────────────────

    @Test
    fun `batches create sets api type, batch size and batch object attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(BATCH_OBJECT_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/messages/batches"))
                    .post(BATCHES_CREATE_REQUEST_BODY.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("batches", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("batches.create", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals(2L, span.attributes[AttributeKey.longKey("gen_ai.request.batch.size")])
            assertEquals("msgbatch_test001", span.attributes[AttributeKey.stringKey("gen_ai.response.batch.id")])
            assertEquals("message_batch", span.attributes[AttributeKey.stringKey("gen_ai.output.type")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.batch.processing_status")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.batch.created_at")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.batch.expires_at")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.response.batch.request_counts.processing")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.response.batch.request_counts.succeeded")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.response.batch.request_counts.errored")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.response.batch.request_counts.canceled")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.response.batch.request_counts.expired")])
        }
    }

    @Test
    fun `batches retrieve sets api type and batch object attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(BATCH_OBJECT_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/messages/batches/msgbatch_test001"))
                    .get()
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("batches", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("batches.retrieve", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("msgbatch_test001", span.attributes[AttributeKey.stringKey("gen_ai.response.batch.id")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.batch.processing_status")])
        }
    }

    @Test
    fun `batches cancel sets api type and batch object attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(BATCH_OBJECT_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/messages/batches/msgbatch_test001/cancel"))
                    .post("".toRequestBody())
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("batches", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("batches.cancel", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("msgbatch_test001", span.attributes[AttributeKey.stringKey("gen_ai.response.batch.id")])
        }
    }

    @Test
    fun `batches list extracts list count`() = runTest {
        withMockServer { server ->
            server.jsonResponse(BATCH_LIST_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/messages/batches"))
                    .get()
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("batches", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("batches.list", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals(1L, span.attributes[AttributeKey.longKey("gen_ai.response.list.count")])
        }
    }

    // ─── Files ─────────────────────────────────────────────────────────────────

    @Test
    fun `files create sets api type and request and response file attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(FILE_OBJECT_RESPONSE)
            val client = instrumentedClient(server)

            val fileBytes = "Hello PDF content".toByteArray()
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    name = "file",
                    filename = "test.pdf",
                    body = fileBytes.toRequestBody("application/pdf".toMediaType())
                )
                .build()

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/files"))
                    .post(multipartBody)
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("files", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("files.create", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("file", span.attributes[AttributeKey.stringKey("gen_ai.output.type")])
            // request attributes
            assertEquals(fileBytes.size.toLong(), span.attributes[AttributeKey.longKey("gen_ai.request.file.size_bytes")])
            assertEquals("test.pdf", span.attributes[AttributeKey.stringKey("gen_ai.request.file.filename")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.request.file.mime_type")])
            // response attributes
            assertEquals("file_abc123", span.attributes[AttributeKey.stringKey("gen_ai.response.file.id")])
            assertEquals("test.pdf", span.attributes[AttributeKey.stringKey("gen_ai.response.file.filename")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.file.mime_type")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.response.file.size_bytes")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.file.downloadable")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.file.created_at")])
        }
    }

    @Test
    fun `files retrieve sets api type and response file attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(FILE_OBJECT_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/files/file_abc123"))
                    .get()
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("files", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("files.retrieve", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("file", span.attributes[AttributeKey.stringKey("gen_ai.output.type")])
            assertEquals("file_abc123", span.attributes[AttributeKey.stringKey("gen_ai.response.file.id")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.file.filename")])
        }
    }

    @Test
    fun `files delete sets api type and file_deleted output type`() = runTest {
        withMockServer { server ->
            server.jsonResponse(FILE_DELETE_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/files/file_abc123"))
                    .delete()
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("files", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("files.delete", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("file_deleted", span.attributes[AttributeKey.stringKey("gen_ai.output.type")])
            assertEquals("file_abc123", span.attributes[AttributeKey.stringKey("gen_ai.response.file.id")])
        }
    }

    @Test
    fun `files list extracts list attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(FILE_LIST_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/files"))
                    .get()
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("files", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("files.list", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals(2L, span.attributes[AttributeKey.longKey("gen_ai.response.list.count")])
            assertEquals("false", span.attributes[AttributeKey.stringKey("gen_ai.response.list.has_more")])
        }
    }

    // ─── Models ────────────────────────────────────────────────────────────────

    @Test
    fun `models retrieve sets api type and all model attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(MODEL_OBJECT_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/models/claude-haiku-4-5"))
                    .get()
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("models", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("models.retrieve", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals("model", span.attributes[AttributeKey.stringKey("gen_ai.output.type")])
            assertEquals("claude-haiku-4-5", span.attributes[AttributeKey.stringKey("gen_ai.request.model")])
            assertEquals("claude-haiku-4-5", span.attributes[AttributeKey.stringKey("gen_ai.response.model")])
            assertEquals("claude-haiku-4-5", span.attributes[AttributeKey.stringKey("gen_ai.response.model.id")])
            assertEquals("Claude Haiku 4.5", span.attributes[AttributeKey.stringKey("gen_ai.response.model.display_name")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.model.created_at")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.response.model.max_input_tokens")])
            assertNotNull(span.attributes[AttributeKey.longKey("gen_ai.response.model.max_output_tokens")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.model.capabilities.vision")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.model.capabilities.batch")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.response.model.capabilities.citations")])
        }
    }

    @Test
    fun `models list extracts list attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(MODEL_LIST_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/models"))
                    .get()
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("models", span.attributes[AttributeKey.stringKey("anthropic.api.type")])
            assertEquals("models.list", span.attributes[AttributeKey.stringKey("gen_ai.operation.name")])
            assertEquals(2L, span.attributes[AttributeKey.longKey("gen_ai.response.list.count")])
        }
    }

    // ─── Error handling ────────────────────────────────────────────────────────

    @Test
    fun `error response sets ERROR status and error attributes`() = runTest {
        withMockServer { server ->
            server.jsonResponse(ERROR_RESPONSE, code = 400)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/messages"))
                    .post(MESSAGES_REQUEST_BODY.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals(StatusCode.ERROR, span.status.statusCode)
            assertEquals(400L, span.attributes[AttributeKey.longKey("http.response.status_code")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.error.message")])
            assertNotNull(span.attributes[AttributeKey.stringKey("gen_ai.error.type")])
        }
    }

    // ─── Common attributes ─────────────────────────────────────────────────────

    @Test
    fun `all requests set server address and port`() = runTest {
        withMockServer { server ->
            server.jsonResponse(MESSAGES_RESPONSE)
            val client = instrumentedClient(server)

            client.newCall(
                Request.Builder()
                    .url(server.url("/v1/messages"))
                    .post(MESSAGES_REQUEST_BODY.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            ).execute().close()

            val span = analyzeSpans().first()
            assertEquals("localhost", span.attributes[AttributeKey.stringKey("server.address")])
            assertNotNull(span.attributes[AttributeKey.longKey("server.port")])
        }
    }

    // ─── Test fixtures ─────────────────────────────────────────────────────────

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val MESSAGES_REQUEST_BODY = """
            {
              "model": "claude-haiku-4-5",
              "max_tokens": 64,
              "messages": [{"role": "user", "content": "Hello"}]
            }
        """.trimIndent()

        val MESSAGES_RESPONSE = """
            {
              "id": "msg_abc123",
              "type": "message",
              "role": "assistant",
              "model": "claude-haiku-4-5",
              "content": [{"type": "text", "text": "Hello!"}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 10, "output_tokens": 5}
            }
        """.trimIndent()

        val COUNT_TOKENS_REQUEST_BODY = """
            {
              "model": "claude-haiku-4-5",
              "messages": [{"role": "user", "content": "Hello, how many tokens is this?"}]
            }
        """.trimIndent()

        val COUNT_TOKENS_RESPONSE = """
            {
              "id": "count_abc123",
              "input_tokens": 12
            }
        """.trimIndent()

        val BATCHES_CREATE_REQUEST_BODY = """
            {
              "requests": [
                {
                  "custom_id": "req-1",
                  "params": {
                    "model": "claude-haiku-4-5",
                    "max_tokens": 64,
                    "messages": [{"role": "user", "content": "Say hi"}]
                  }
                },
                {
                  "custom_id": "req-2",
                  "params": {
                    "model": "claude-haiku-4-5",
                    "max_tokens": 64,
                    "messages": [{"role": "user", "content": "Say bye"}]
                  }
                }
              ]
            }
        """.trimIndent()

        val BATCH_OBJECT_RESPONSE = """
            {
              "id": "msgbatch_test001",
              "type": "message_batch",
              "processing_status": "in_progress",
              "request_counts": {
                "processing": 2,
                "succeeded": 0,
                "errored": 0,
                "canceled": 0,
                "expired": 0
              },
              "ended_at": null,
              "created_at": "2026-01-01T00:00:00Z",
              "expires_at": "2026-01-02T00:00:00Z",
              "cancel_initiated_at": null,
              "results_url": null
            }
        """.trimIndent()

        val BATCH_LIST_RESPONSE = """
            {
              "data": [
                {
                  "id": "msgbatch_test001",
                  "type": "message_batch",
                  "processing_status": "ended"
                }
              ],
              "has_more": false,
              "first_id": "msgbatch_test001",
              "last_id": "msgbatch_test001"
            }
        """.trimIndent()

        val FILE_OBJECT_RESPONSE = """
            {
              "id": "file_abc123",
              "type": "file",
              "filename": "test.pdf",
              "mime_type": "application/pdf",
              "size": 1024,
              "downloadable": false,
              "created_at": "2026-01-01T00:00:00Z"
            }
        """.trimIndent()

        val FILE_DELETE_RESPONSE = """
            {
              "id": "file_abc123",
              "deleted": true
            }
        """.trimIndent()

        val FILE_LIST_RESPONSE = """
            {
              "data": [
                {"id": "file_abc123", "type": "file", "filename": "a.pdf"},
                {"id": "file_def456", "type": "file", "filename": "b.pdf"}
              ],
              "has_more": false
            }
        """.trimIndent()

        val MODEL_OBJECT_RESPONSE = """
            {
              "id": "claude-haiku-4-5",
              "type": "model",
              "display_name": "Claude Haiku 4.5",
              "created_at": "2026-01-01T00:00:00Z",
              "max_input_tokens": 200000,
              "max_output_tokens": 8192,
              "capabilities": {
                "vision": true,
                "batch": true,
                "citations": true
              }
            }
        """.trimIndent()

        val MODEL_LIST_RESPONSE = """
            {
              "data": [
                {"id": "claude-haiku-4-5", "type": "model", "display_name": "Claude Haiku 4.5"},
                {"id": "claude-sonnet-4-5", "type": "model", "display_name": "Claude Sonnet 4.5"}
              ],
              "has_more": false
            }
        """.trimIndent()

        val ERROR_RESPONSE = """
            {
              "error": {
                "type": "invalid_request_error",
                "message": "messages: field required"
              }
            }
        """.trimIndent()
    }
}
