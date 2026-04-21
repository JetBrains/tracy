/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.core.adapters

import org.jetbrains.ai.tracy.core.http.protocol.*
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GEN_AI_SYSTEM
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging


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
        span.updateName(getSpanName(request))

        // Pre-allocate in case the span reaches the limit
        span.setAttribute(DROPPED_ATTRIBUTES_COUNT_ATTRIBUTE_KEY, 0L)

        getRequestBodyAttributes(span, request)
        span.setAttribute("gen_ai.api_base", "${request.url.scheme}://${request.url.host}")
        span.setAttribute(GEN_AI_SYSTEM, genAISystem)
        span.setAttribute("gen_ai.provider.name", genAISystem)
        span.setAttribute("server.address", request.url.host)
        span.setAttribute("server.port", request.url.port.toLong())

        return@runCatching
    }.getOrElse { exception ->
        span.setStatus(StatusCode.ERROR)
        span.recordException(exception)
    }

    fun registerResponse(span: Span, response: TracyHttpResponse): Unit =
        runCatching {
            // Always record the HTTP status code first, regardless of body-parsing outcomes
            span.setAttribute("http.status_code", response.code.toLong())

            val body = response.body.asJson()?.jsonObject ?: return
            val isStreamingRequest = body["stream"]?.jsonPrimitive?.boolean == true
            val mimeType = response.contentType?.mimeType

            if (mimeType != null) {
                when {
                    isStreamingRequest && mimeType == TracyContentType.Text.EventStream.mimeType -> {
                        span.setAttribute("gen_ai.response.streaming", true)
                        span.setAttribute("gen_ai.completion.content.type", response.contentType?.asString())
                    }
                    mimeType != TracyContentType.Text.EventStream.mimeType -> {
                        // mime type can be application/json, video/mp4 (for OpenAI Video API), etc.
                        runCatching {
                            getResponseBodyAttributes(span, response)
                        }.getOrElse { exception ->
                            logger.warn(exception) { "Failed to extract response body attributes" }
                        }
                    }
                    else -> {
                        span.setAttribute("gen_ai.completion.content.type", response.contentType?.asString())
                    }
                }
            }

            if (response.isError()) {
                getResponseErrorBodyAttributes(span, response.body)
                // Ensure OTel-required error.type is always set for HTTP error responses.
                // getResponseErrorBodyAttributes populates it from the provider body when available;
                // fall back to the HTTP status code for non-standard or non-JSON bodies
                // (e.g. gateway errors, CDN rejections).
                val hasErrorType = (span as? ReadableSpan)?.toSpanData()?.attributes
                    ?.get(AttributeKey.stringKey("error.type")) != null
                if (!hasErrorType) {
                    span.setAttribute("error.type", "${response.code}")
                }
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
            (error["message"] as? JsonPrimitive)?.let { span.setAttribute("gen_ai.error.message", it.content) }
            (error["type"] as? JsonPrimitive)?.let {
                span.setAttribute("gen_ai.error.type", it.content)
                span.setAttribute("error.type", it.content)
            }
            (error["param"] as? JsonPrimitive)?.let { span.setAttribute("gen_ai.error.param", it.content) }
            (error["code"] as? JsonPrimitive)?.let { span.setAttribute("gen_ai.error.code", it.content) }
        }
    }

    protected abstract fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest)
    protected abstract fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse)

    abstract fun getSpanName(request: TracyHttpRequest): String
    abstract fun isStreamingRequest(request: TracyHttpRequest): Boolean
    abstract fun handleStreaming(span: Span, url: TracyHttpUrl, events: String)

    private val logger = KotlinLogging.logger {}

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
