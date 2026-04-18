/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.instrumentation

import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
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

class OpenTelemetryOkHttpInterceptorHttpAttributesTest : BaseOpenTelemetryTracingTest() {

    private class TestLLMTracingAdapter : LLMTracingAdapter("test-provider") {
        override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {}
        override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {}
        override fun getSpanName(request: TracyHttpRequest) = "test-span"
        override fun isStreamingRequest(request: TracyHttpRequest) = false
        override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {}
    }

    @Test
    fun `test http_request_method attribute is set for POST request`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val client = instrument(OkHttpClient(), TestLLMTracingAdapter())
            val request = Request.Builder()
                .url(server.url("/v1/chat/completions"))
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use {}

            val spans = analyzeSpans()
            assertEquals(1, spans.size)
            assertEquals("POST", spans.first().attributes[HttpAttributes.HTTP_REQUEST_METHOD])
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `test url_full attribute is set on span`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val client = instrument(OkHttpClient(), TestLLMTracingAdapter())
            val expectedUrl = server.url("/v1/chat/completions").toString()
            val request = Request.Builder()
                .url(expectedUrl)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use {}

            val spans = analyzeSpans()
            assertEquals(1, spans.size)
            assertEquals(expectedUrl, spans.first().attributes[UrlAttributes.URL_FULL])
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `test http_request_method attribute is set for GET request`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val client = instrument(OkHttpClient(), TestLLMTracingAdapter())
            val request = Request.Builder()
                .url(server.url("/v1/models"))
                .get()
                .build()

            client.newCall(request).execute().use {}

            val spans = analyzeSpans()
            assertEquals(1, spans.size)
            assertEquals("GET", spans.first().attributes[HttpAttributes.HTTP_REQUEST_METHOD])
        } finally {
            server.shutdown()
        }
    }
}
