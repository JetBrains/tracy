/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core

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
import org.jetbrains.ai.tracy.test.utils.BaseOpenTelemetryTracingTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OpenTelemetryOkHttpInterceptorTest : BaseOpenTelemetryTracingTest() {
    private lateinit var server: MockWebServer

    private val adapter = object : LLMTracingAdapter("test-system") {
        override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {}
        override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {}
        override fun getSpanName(request: TracyHttpRequest) = "test-span"
        override fun isStreamingRequest(request: TracyHttpRequest) = false
        override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {}
    }

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun `http request method attribute reflects POST`() = runTest {
        server.enqueue(MockResponse().setBody("{}").addHeader("Content-Type", "application/json"))

        val client = instrument(OkHttpClient(), adapter)
        val request = Request.Builder()
            .url(server.url("/v1/chat/completions"))
            .post("""{"model":"test"}""".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use {}

        val span = analyzeSpans().first()
        assertEquals("POST", span.attributes[AttributeKey.stringKey("http.request.method")])
    }

    @Test
    fun `http request method attribute reflects GET`() = runTest {
        server.enqueue(MockResponse().setBody("{}").addHeader("Content-Type", "application/json"))

        val client = instrument(OkHttpClient(), adapter)
        val request = Request.Builder()
            .url(server.url("/v1/models"))
            .get()
            .build()
        client.newCall(request).execute().use {}

        val span = analyzeSpans().first()
        assertEquals("GET", span.attributes[AttributeKey.stringKey("http.request.method")])
    }

    @Test
    fun `url full attribute contains scheme, host, non-default port, and path`() = runTest {
        server.enqueue(MockResponse().setBody("{}").addHeader("Content-Type", "application/json"))

        val client = instrument(OkHttpClient(), adapter)
        val serverUrl = server.url("/v1/chat/completions")
        val request = Request.Builder()
            .url(serverUrl)
            .post("""{"model":"test"}""".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use {}

        val span = analyzeSpans().first()
        val expected = "http://${serverUrl.host}:${serverUrl.port}/v1/chat/completions"
        assertEquals(expected, span.attributes[AttributeKey.stringKey("url.full")])
    }
}
