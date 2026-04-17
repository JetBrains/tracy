/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.interceptor

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.http.protocol.*
import org.jetbrains.ai.tracy.core.instrument
import org.jetbrains.ai.tracy.test.utils.BaseAITracingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OpenTelemetryOkHttpInterceptorTest : BaseAITracingTest() {

    @Test
    fun `interceptor sets http_request_method attribute`() = runTest {
        withMockServer { server ->
            server.enqueue(MockResponse().setBody("{}").setHeader("Content-Type", "application/json"))

            val client = instrument(OkHttpClient(), StubLLMTracingAdapter())
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
        }
    }

    @Test
    fun `interceptor sets url_full attribute`() = runTest {
        withMockServer { server ->
            server.enqueue(MockResponse().setBody("{}").setHeader("Content-Type", "application/json"))

            val client = instrument(OkHttpClient(), StubLLMTracingAdapter())
            val url = server.url("/v1/chat/completions")

            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use {}

            val spans = analyzeSpans()
            assertNotNull(spans.firstOrNull(), "Expected at least one span")
            val span = spans.first()

            val urlFull = span.attributes[AttributeKey.stringKey("url.full")]
            assertNotNull(urlFull, "url.full attribute should be present")
            assertEquals("http", url.scheme)
            assertEquals(url.host, url.host)
            assert(urlFull.contains("/v1/chat/completions")) {
                "url.full should contain the request path, got: $urlFull"
            }
        }
    }
}

private class StubLLMTracingAdapter : LLMTracingAdapter("stub") {
    override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) = Unit
    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) = Unit
    override fun getSpanName(request: TracyHttpRequest) = "stub.request"
    override fun isStreamingRequest(request: TracyHttpRequest) = false
    override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) = Unit
}
