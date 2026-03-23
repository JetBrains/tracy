/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.adapters

import io.opentelemetry.api.common.AttributeKey
import org.jetbrains.ai.tracy.core.http.protocol.*
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent


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
 * class AnthropicLLMTracingAdapter : LLMTracingAdapter(GenAiSystemIncubatingValues.ANTHROPIC) {
 *     override fun getRequestBodyAttributes(span: Span, request: Request) {
 *         // Parse Anthropic-specific request format
 *     }
 *     override fun getResponseBodyAttributes(span: Span, response: Response) {
 *         // Parse Anthropic-specific response format
 *     }
 *     override fun getSpanName(request: Request) = "Anthropic-generation"
 *     override fun isStreamingRequest(request: Request) = false
 *     override fun handleStreaming(span: Span, url: Url, events: String) = Unit
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
            val contentType = response.contentType
            // install the content type of the response body
            contentType?.asString()?.let {
                span.setAttribute("gen_ai.completion.content.type", it)
            }

            // set response body attributes only for `application/json` content types;
            // stream events are handled by `registerResponseStreamEvent`
            when (contentType?.mimeType) {
                TracyContentType.Application.Json.mimeType -> {
                    // TODO: register non-JSON and non-SSE responses also (e.g., video/mp4)
                    getResponseBodyAttributes(span, response)
                }
                TracyContentType.Text.EventStream.mimeType -> {
                    span.setAttribute("gen_ai.response.streaming", true)
                }
                else -> {
                    logger.warn("Unsupported content type: ${contentType?.asString()}")
                }
            }

            span.setAttribute("http.status_code", response.code.toLong())

            if (response.isError()) {
                getResponseErrorBodyAttributes(span, response)
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

    protected open fun getResponseErrorBodyAttributes(span: Span, response: TracyHttpResponse) {
        // parse only `application/json` responses
        if (response.contentType?.mimeType != TracyContentType.Application.Json.mimeType) {
            return
        }
        val body = response.body.asJson()?.jsonObject ?: return

        body["error"]?.jsonObject?.let { error ->
            error["message"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.message", it.content) }
            error["type"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.type", it.content) }
            error["param"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.param", it.content) }
            error["code"]?.jsonPrimitive?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    protected abstract fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest)
    protected abstract fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse)

    abstract fun getSpanName(): String

    // handling streaming API (server-sent events)
    fun registerResponseStreamEvent(span: Span, url: TracyHttpUrl, event: SseEvent) {
        // factory method workflow:
        //  1. extract the index of the current event from span (0 when missing)
        //  2. delegate assigning to the implementation:
        //     - when assigned successfully, increment the index and store in span
        val nextEventIndex = (span as? ReadableSpan)?.attributes?.get(STREAM_EVENTS_COUNT_KEY) ?: 0

        val result = registerResponseStreamEvent(span, url, event, index = nextEventIndex)
        if (result.isSuccess) {
            // event was successfully assigned into span
            span.setAttribute(STREAM_EVENTS_COUNT_KEY, nextEventIndex + 1)
        }
    }

    /**
     * @return true if the event was successfully assigned into the span, false otherwise
     */
    protected abstract fun registerResponseStreamEvent(span: Span, url: TracyHttpUrl, event: SseEvent, index: Long): Result<Unit>

    companion object {
        private val DROPPED_ATTRIBUTES_COUNT_ATTRIBUTE_KEY = AttributeKey.longKey("otel.dropped_attributes_count")
        private val STREAM_EVENTS_COUNT_KEY = AttributeKey.longKey("tracy.response.sse.events.count")

        private val logger = KotlinLogging.logger {}

        /**
         * Adds unmapped payload attributes from a JSON body to the given [Span].
         *
         * @param body the request or response body
         * @param mappedAttributes a list of attribute keys that have already been
         *  handled and should be skipped when populating unmapped attributes
         */
        // TODO: should be protected/internal?
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
