/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.http

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
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

class OpenTelemetryOkHttpInterceptorTest : BaseOpenTelemetryTracingTest() {

    private val testAdapter = object : LLMTracingAdapter("test-system") {
        override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) = Unit
        override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) = Unit
        override fun getSpanName(request: TracyHttpRequest) = "test-span"
        override fun isStreamingRequest(request: TracyHttpRequest) = false
        override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) = Unit
    }

    @Test
    fun `sets http_request_method attribute for GET request`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200))

            val client = instrument(OkHttpClient(), testAdapter)
            val request = Request.Builder()
                .url(server.url("/v1/test"))
                .get()
                .build()

            client.newCall(request).execute().use { }

            val spans = analyzeSpans()
            assertEquals(1, spans.size)
            assertEquals("GET", spans.first().attributes[AttributeKey.stringKey("http.request.method")])
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `sets http_request_method attribute for POST request`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200))

            val client = instrument(OkHttpClient(), testAdapter)
            val request = Request.Builder()
                .url(server.url("/v1/test"))
                .post("{}".toRequestBody())
                .build()

            client.newCall(request).execute().use { }

            val spans = analyzeSpans()
            assertEquals(1, spans.size)
            assertEquals("POST", spans.first().attributes[AttributeKey.stringKey("http.request.method")])
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `sets url_full attribute with scheme, host, port, and path`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200))

            val client = instrument(OkHttpClient(), testAdapter)
            val serverUrl = server.url("/v1/chat/completions")
            val request = Request.Builder()
                .url(serverUrl)
                .get()
                .build()

            client.newCall(request).execute().use { }

            val spans = analyzeSpans()
            assertEquals(1, spans.size)
            val urlFull = spans.first().attributes[AttributeKey.stringKey("url.full")]
            assertNotNull(urlFull)
            assertEquals(serverUrl.toString(), urlFull)
        } finally {
            server.shutdown()
        }
    }
}
