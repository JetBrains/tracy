/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.adapters

import org.jetbrains.ai.tracy.core.http.protocol.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


/**
 * Base adapter for tracing LLM provider API interactions using OpenTelemetry.
 *
 * This abstract class provides the foundation for implementing provider-specific tracing adapters.
 * It handles HTTP request/response interception, attribute extraction, error handling, and
 * streaming support. Subclasses implement provider-specific parsing logic for different API formats
 * (OpenAI, Anthropic, Gemini, etc.).
 *
 * ## Usage
 * Extend this class to create a provider-specific adapter:
 * ```kotlin
 * class MyProviderAdapter : LLMTracingAdapter("my-provider") {
 *     override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {
 *         // Parse provider-specific request format (model, messages, tools, etc.)
 *     }
 *     override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
 *         // Parse provider-specific response format (completions, usage, etc.)
 *     }
 *     override fun getSpanName(request: TracyHttpRequest) = "MyProvider-generation"
 *     override fun isStreamingRequest(request: TracyHttpRequest): Boolean {
 *         // Check for streaming indicator (e.g., "stream": true in body, or URL-based)
 *     }
 *     override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {
 *         // Parse SSE events and set span attributes.
 *     }
 * }
 * ```
 *
 * Use with the `instrument()` function:
 * ```kotlin
 * val client = instrument(HttpClient(), AnthropicLLMTracingAdapter())
 * ```
 *
 * @param genAISystem The name of the GenAI system (e.g., "openai", "anthropic", "gemini")
 */
abstract class LLMTracingAdapter(private val genAISystem: String) {
    fun registerRequest(span: Span, request: TracyHttpRequest): Unit = runCatching {
        span.updateName(getSpanName(request))

        // Pre-allocate in case the span reaches the limit
        span.setAttribute(DROPPED_ATTRIBUTES_COUNT_ATTRIBUTE_KEY, 0L)

        getRequestBodyAttributes(span, request)
        span.setAttribute("gen_ai.api_base", "${request.url.scheme}://${request.url.host}")
        span.setAttribute(GEN_AI_SYSTEM, genAISystem)

        return@runCatching
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    fun registerResponse(span: Span, response: TracyHttpResponse): Unit =
        runCatching {
            val body = response.body.asJson()?.jsonObject ?: return
            val isStreamingRequest = body["stream"]?.jsonPrimitive?.boolean == true
            val mimeType = response.contentType?.mimeType

            if (mimeType != null) {
                when {
                    mimeType == TracyContentType.Application.Json.mimeType -> {
                        getResponseBodyAttributes(span, response)
                    }
                    isStreamingRequest && mimeType == TracyContentType.Text.EventStream.mimeType -> {
                        span.setAttribute("gen_ai.response.streaming", true)
                        span.setAttribute("gen_ai.completion.content.type", response.contentType?.asString())
                    }
                    else -> {
                        span.setAttribute("gen_ai.completion.content.type", response.contentType?.asString())
                    }
                }
            }

            span.setAttribute("http.status_code", response.code.toLong())

            if (response.isError()) {
                getResponseErrorBodyAttributes(span, response.body)
                span.setStatus(StatusCode.ERROR)
            } else {
                span.setStatus(StatusCode.OK)
            }

            val spanData = (span as? ReadableSpan)?.toSpanData() ?: return@runCatching
            val totalAttributesCount = spanData.totalAttributeCount
            val keptAttributesCount = spanData.attributes.size()

            // Calculate the number of attributes that were dropped due to span limits
            val droppedAttributesCount = (totalAttributesCount - keptAttributesCount).coerceAtLeast(0).toLong()

            span.setAttribute(DROPPED_ATTRIBUTES_COUNT_ATTRIBUTE_KEY, droppedAttributesCount)
        }.getOrElse { exception ->
            span.setStatus(StatusCode.ERROR)
            span.recordException(exception)
        }

    protected open fun getResponseErrorBodyAttributes(span: Span, body: TracyHttpResponseBody) {
        body.asJson()?.jsonObject["error"]?.jsonObject?.let { error ->
            error["message"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.message", it.content) }
            error["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.type", it.content) }
            error["param"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.param", it.content) }
            error["code"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    protected abstract fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest)
    protected abstract fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse)

    abstract fun getSpanName(request: TracyHttpRequest): String

    /**
     * Returns `true` if the request is a streaming request.
     */
    abstract fun isStreamingRequest(request: TracyHttpRequest): Boolean

    /**
     * Extracts span attributes from a streaming response.
     */
    abstract fun handleStreaming(span: Span, url: TracyHttpUrl, events: String)

    companion object {
        private const val DROPPED_ATTRIBUTES_COUNT_ATTRIBUTE_KEY = "otel.dropped_attributes_count"

        /**
         * Adds unmapped payload attributes from a JSON body to the given [Span].
         *
         * @param body the request or response body
         * @param mappedAttributes a list of attribute keys that have already been
         *  handled and should be skipped when populating unmapped attributes
         */
        fun Span.populateUnmappedAttributes(
            body: JsonObject,
            mappedAttributes: List<String>,
            payloadType: PayloadType
        ) {
            body.entries.forEach { (key, value) ->
                if (key !in (mappedAttributes)) {
                    val attributeKey = "tracy.${payloadType.value}.$key"
                    setAttribute(attributeKey, value.toString())
                }
            }
        }

        enum class PayloadType(val value: String) {
            REQUEST("request"),
            RESPONSE("response")
        }
    }
}
