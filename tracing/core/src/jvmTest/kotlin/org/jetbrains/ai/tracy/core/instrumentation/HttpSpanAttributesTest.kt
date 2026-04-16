/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.instrumentation

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.instrument
import org.jetbrains.ai.tracy.test.utils.BaseOpenTelemetryTracingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HttpSpanAttributesTest : BaseOpenTelemetryTracingTest() {

    private val adapter = object : LLMTracingAdapter("test-system") {
        override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {}
        override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {}
        override fun getSpanName(request: TracyHttpRequest) = "test-span"
        override fun isStreamingRequest(request: TracyHttpRequest) = false
        override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {}
    }

    @Test
    fun `http request method and full url are set as span attributes`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").addHeader("Content-Type", "application/json"))

        try {
            val client = instrument(OkHttpClient(), adapter)
            val url = server.url("/v1/chat/completions")

            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use {}

            val spans = analyzeSpans()
            assertNotNull(spans.firstOrNull(), "Expected at least one span")
            val span = spans.first()

            assertEquals(
                "POST",
                span.attributes[AttributeKey.stringKey("http.request.method")],
                "http.request.method should be POST"
            )
            assertEquals(
                "http://${server.hostName}:${server.port}/v1/chat/completions",
                span.attributes[AttributeKey.stringKey("url.full")],
                "url.full should contain the full request URL"
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `http request method is GET for get requests`() = runTest {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}").addHeader("Content-Type", "application/json"))

        try {
            val client = instrument(OkHttpClient(), adapter)
            val url = server.url("/v1/models")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use {}

            val spans = analyzeSpans()
            assertNotNull(spans.firstOrNull(), "Expected at least one span")
            val span = spans.first()

            assertEquals(
                "GET",
                span.attributes[AttributeKey.stringKey("http.request.method")],
                "http.request.method should be GET"
            )
            assertEquals(
                "http://${server.hostName}:${server.port}/v1/models",
                span.attributes[AttributeKey.stringKey("url.full")],
                "url.full should contain the full request URL"
            )
        } finally {
            server.shutdown()
        }
    }
}
