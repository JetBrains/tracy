/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.adapters

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.test.runTest
import org.jetbrains.ai.tracy.core.TracingManager
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequestBody
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrlImpl
import org.jetbrains.ai.tracy.core.http.protocol.TracyQueryParameters
import org.jetbrains.ai.tracy.core.http.protocol.asRequestView
import org.jetbrains.ai.tracy.test.utils.BaseOpenTelemetryTracingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LLMTracingAdapterHttpAttributesTest : BaseOpenTelemetryTracingTest() {

    @Test
    fun `registerRequest sets http request method attribute`() = runTest {
        val span = TracingManager.tracer.spanBuilder("test").startSpan()
        val request = buildRequest(method = "POST", pathSegments = listOf("v1", "chat", "completions"))

        TestLLMTracingAdapter().registerRequest(span, request)
        span.end()

        val trace = analyzeSpans().first()
        assertEquals("POST", trace.attributes[AttributeKey.stringKey("http.request.method")])
    }

    @Test
    fun `registerRequest sets url full attribute`() = runTest {
        val span = TracingManager.tracer.spanBuilder("test").startSpan()
        val request = buildRequest(
            scheme = "https",
            host = "api.openai.com",
            pathSegments = listOf("v1", "chat", "completions"),
        )

        TestLLMTracingAdapter().registerRequest(span, request)
        span.end()

        val trace = analyzeSpans().first()
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            trace.attributes[AttributeKey.stringKey("url.full")],
        )
    }

    @Test
    fun `registerRequest url full preserves trailing slash for root path`() = runTest {
        val span = TracingManager.tracer.spanBuilder("test").startSpan()
        val request = buildRequest(
            scheme = "https",
            host = "api.openai.com",
            pathSegments = listOf(""),
        )

        TestLLMTracingAdapter().registerRequest(span, request)
        span.end()

        val trace = analyzeSpans().first()
        assertNotNull(trace.attributes[AttributeKey.stringKey("url.full")])
    }

    @Test
    fun `registerRequest http method is uppercased`() = runTest {
        val span = TracingManager.tracer.spanBuilder("test").startSpan()
        val request = buildRequest(method = "get", pathSegments = listOf("v1", "models"))

        TestLLMTracingAdapter().registerRequest(span, request)
        span.end()

        val trace = analyzeSpans().first()
        assertEquals("GET", trace.attributes[AttributeKey.stringKey("http.request.method")])
    }

    private fun buildRequest(
        method: String = "POST",
        scheme: String = "https",
        host: String = "api.test.com",
        pathSegments: List<String> = emptyList(),
    ): TracyHttpRequest {
        val url = TracyHttpUrlImpl(
            scheme = scheme,
            host = host,
            pathSegments = pathSegments,
            parameters = object : TracyQueryParameters {
                override fun queryParameter(name: String): String? = null
                override fun queryParameterValues(name: String): List<String?> = emptyList()
            },
        )
        return TracyHttpRequestBody.Empty.asRequestView(
            contentType = null,
            url = url,
            method = method,
        )
    }

    private class TestLLMTracingAdapter : LLMTracingAdapter("test-provider") {
        override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {}
        override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {}
        override fun getSpanName(request: TracyHttpRequest) = "test-span"
        override fun isStreamingRequest(request: TracyHttpRequest) = false
        override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {}
    }
}
