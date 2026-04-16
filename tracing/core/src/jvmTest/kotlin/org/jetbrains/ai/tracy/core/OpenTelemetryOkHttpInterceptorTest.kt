/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.test.utils.BaseAITracingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenTelemetryOkHttpInterceptorTest : BaseAITracingTest() {

    private val testAdapter = object : LLMTracingAdapter("test-system") {
        override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {}
        override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {}
        override fun getSpanName(request: TracyHttpRequest) = "test-span"
        override fun isStreamingRequest(request: TracyHttpRequest) = false
        override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {}
    }

    @Test
    fun `test http_request_method attribute is set on span`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}")
            )

            val client = instrument(OkHttpClient(), testAdapter)
            val request = Request.Builder()
                .url(server.url("/v1/chat/completions"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { }

            val trace = analyzeSpans().first()
            assertEquals("POST", trace.attributes[AttributeKey.stringKey("http.request.method")])
        }
    }

    @Test
    fun `test url_full attribute is set on span`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}")
            )

            val client = instrument(OkHttpClient(), testAdapter)
            val request = Request.Builder()
                .url(server.url("/v1/chat/completions"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { }

            val trace = analyzeSpans().first()
            val urlFull = trace.attributes[AttributeKey.stringKey("url.full")]
            assertNotNull(urlFull)
            assertTrue(urlFull.startsWith("http://"))
            assertTrue(urlFull.endsWith("/v1/chat/completions"))
        }
    }

    @Test
    fun `test url_full and http_request_method are set for GET requests`() = runTest {
        withMockServer { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}")
            )

            val client = instrument(OkHttpClient(), testAdapter)
            val request = Request.Builder()
                .url(server.url("/v1/models"))
                .get()
                .build()

            client.newCall(request).execute().use { }

            val trace = analyzeSpans().first()
            assertEquals("GET", trace.attributes[AttributeKey.stringKey("http.request.method")])
            val urlFull = trace.attributes[AttributeKey.stringKey("url.full")]
            assertNotNull(urlFull)
            assertTrue(urlFull.endsWith("/v1/models"))
        }
    }
}
