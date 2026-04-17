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
    fun `http request method and url full attributes are set on span`() = runTest {
        server.enqueue(MockResponse().setBody("{}").setResponseCode(200))

        val client = instrument(OkHttpClient(), StubLLMTracingAdapter())
        val request = Request.Builder()
            .url(server.url("/v1/chat/completions"))
            .post(ByteArray(0).toRequestBody())
            .build()

        client.newCall(request).execute().use {}

        val spans = analyzeSpans()
        assertEquals(1, spans.size)
        val span = spans.first()

        assertEquals("POST", span.attributes[AttributeKey.stringKey("http.request.method")])

        val expectedUrl = server.url("/v1/chat/completions").toString()
        assertEquals(expectedUrl, span.attributes[AttributeKey.stringKey("url.full")])
    }

    @Test
    fun `http request method is uppercase`() = runTest {
        server.enqueue(MockResponse().setBody("{}").setResponseCode(200))

        val client = instrument(OkHttpClient(), StubLLMTracingAdapter())
        val request = Request.Builder()
            .url(server.url("/v1/models"))
            .get()
            .build()

        client.newCall(request).execute().use {}

        val spans = analyzeSpans()
        assertEquals(1, spans.size)
        assertEquals("GET", spans.first().attributes[AttributeKey.stringKey("http.request.method")])
    }
}

private class StubLLMTracingAdapter : LLMTracingAdapter("test-system") {
    override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) = Unit
    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) = Unit
    override fun getSpanName(request: TracyHttpRequest) = "test-span"
    override fun isStreamingRequest(request: TracyHttpRequest) = false
    override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) = Unit
}
