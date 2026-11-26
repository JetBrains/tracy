/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.anthropic.adapters

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.CountTokensAnthropicApiEndpointHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.batches.BatchesAnthropicApiEndpointHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.files.FilesAnthropicApiEndpointHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.messages.MessagesAnthropicApiEndpointHandler
import org.jetbrains.ai.tracy.anthropic.adapters.handlers.models.ModelsAnthropicApiEndpointHandler
import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractor
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractorImpl
import org.jetbrains.ai.tracy.core.http.parsers.SseEvent
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpRequest
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpResponse
import org.jetbrains.ai.tracy.core.http.protocol.TracyHttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects which Anthropic API is being used based on the request / response URL.
 *
 * Order matters: `count_tokens` and `batches` URLs both live under `/v1/messages/...`
 * and therefore contain the `messages` segment too. They must be matched BEFORE
 * [MESSAGES] so the more specific route wins.
 */
private enum class AnthropicApiType(val route: String, val apiTypeName: String) {
    // See: https://docs.anthropic.com/en/api/messages-count-tokens
    COUNT_TOKENS("count_tokens", "count_tokens"),

    // See: https://docs.anthropic.com/en/api/creating-message-batches
    BATCHES("batches", "batches"),

    // See: https://docs.anthropic.com/en/api/files-list
    FILES("files", "files"),

    // See: https://docs.anthropic.com/en/api/models-list
    MODELS("models", "models"),

    // See: https://docs.claude.com/en/api/messages
    MESSAGES("messages", "messages");

    companion object {
        fun detect(url: TracyHttpUrl): AnthropicApiType? {
            val route = url.pathSegments.joinToString(separator = "/")
            return entries.firstOrNull { route.contains(it.route) }
        }
    }
}

/**
 * Tracing adapter for Anthropic Claude API.
 *
 * Automatically detects and handles multiple Anthropic API endpoints including messages,
 * count tokens, message batches, files, and models. Uses specialized handlers for each
 * endpoint type to extract telemetry data including model parameters, messages, tool calls,
 * usage statistics, and media content.
 *
 * ## Supported Endpoints
 * - **Messages**: `/v1/messages`
 * - **Count Tokens**: `/v1/messages/count_tokens`
 * - **Message Batches**: `/v1/messages/batches`
 * - **Files**: `/v1/files`
 * - **Models**: `/v1/models`
 *
 * ## Example Usage
 * ```kotlin
 * val client = instrument(HttpClient(), AnthropicLLMTracingAdapter())
 * client.post("https://api.anthropic.com/v1/messages") {
 *     header("x-api-key", apiKey)
 *     header("anthropic-version", "2023-06-01")
 *     setBody("""
 *         {
 *             "max_tokens": 1024,
 *             "messages": [{"content": "Hello!", "role": "user"}],
 *             "model": "claude-3-7-sonnet-latest"
 *         }
 *     """)
 * }
 * // Automatically traces request/response with tool calls and media content
 * ```
 *
 * See: [Anthropic Messages API](https://docs.claude.com/en/api/messages)
 */
class AnthropicLLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.ANTHROPIC) {
    private val extractor: MediaContentExtractor = MediaContentExtractorImpl()
    private val batchesHandler = BatchesAnthropicApiEndpointHandler()
    private val countTokensHandler = CountTokensAnthropicApiEndpointHandler()
    private val filesHandler = FilesAnthropicApiEndpointHandler(extractor)
    private val modelsHandler = ModelsAnthropicApiEndpointHandler()

    private val handlers = ConcurrentHashMap<AnthropicApiType, EndpointApiHandler>()

    override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {
        handlerFor(request.url).handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        handlerFor(response.url).handleResponseAttributes(span, response)
    }

    /**
     * Overrides error-body attribute extraction to guarantee that `error.type` is always populated
     * for HTTP error responses, even when the response body does not conform to the standard
     * Anthropic error envelope (`{"error": {"type": "...", "message": "..."}}`).
     *
     * Examples where the standard envelope is absent:
     * - Batch requests with an empty `requests` array return `{"detail": "..."}`.
     * - Proxy / gateway errors may return a plain HTML or non-JSON body.
     *
     * Fallback mapping:
     * - 4xx → `"invalid_request_error"`
     * - 5xx → `"internal_error"`
     */
    override fun getResponseErrorBodyAttributes(span: Span, response: TracyHttpResponse) {
        super.getResponseErrorBodyAttributes(span, response)

        // anthropic.api.type is written synchronously to the span by every handler during
        // request processing. Reading from the span is reliable across OkHttp's async dispatch
        // (spans are shared, thread-safe objects) and survives redirects that may rewrite
        // response.url. Fall back to URL detection only if the request handler was never reached
        // (e.g., a malformed body short-circuited request parsing before the api.type was set).
        val spanApiType = (span as? ReadableSpan)
            ?.toSpanData()
            ?.attributes
            ?.get(AttributeKey.stringKey("anthropic.api.type"))
        val apiType = spanApiType ?: AnthropicApiType.detect(response.url)?.apiTypeName

        if (apiType != null) {
            span.setAttribute("anthropic.api.type", apiType)
            span.setAttribute("gen_ai.provider.name", GenAiSystemIncubatingValues.ANTHROPIC)
            if (apiType == "batches") {
                // Preserve the operation name already written to the span during request processing.
                // Calling detectOperation() on the response URL is unreliable: after a redirect the
                // URL may no longer contain the "batches" segment, and a redirect-rewritten HTTP
                // method (e.g. POST→GET after a 302) would produce the wrong operation name.
                val existingOpName = (span as? ReadableSpan)
                    ?.toSpanData()
                    ?.attributes
                    ?.get(GEN_AI_OPERATION_NAME)
                if (existingOpName.isNullOrBlank()) {
                    span.setAttribute(
                        GEN_AI_OPERATION_NAME,
                        batchesHandler.detectOperation(response.url, response.requestMethod)
                    )
                }
            }
        }

        // If the base class already extracted error.type from the body, nothing more to do.
        val alreadySet = (span as? ReadableSpan)
            ?.toSpanData()
            ?.attributes
            ?.get(AttributeKey.stringKey("error.type"))
        if (alreadySet != null) return

        // Derive fallback error.type from the HTTP status code directly from the response.
        val fallbackType = when (response.code) {
            in 400..499 -> "invalid_request_error"
            in 500..599 -> "internal_error"
            else -> return
        }
        span.setAttribute("error.type", fallbackType)
    }

    /**
     * Sets a default span name to **"Anthropic-generation"**.
     *
     * For the messages endpoint this name is overridden during response processing to follow
     * GenAI Conventions for Anthropic:
     * ```
     * {gen_ai.operation.name} {gen_ai.request.model}
     * ```
     *
     * See [GenAI Anthropic Spans](https://opentelemetry.io/docs/specs/semconv/gen-ai/anthropic/#spans)
     */
    override fun getSpanName() = "Anthropic-generation"

    override fun registerResponseStreamEvent(
        span: Span,
        url: TracyHttpUrl,
        event: SseEvent,
        index: Long,
    ): Result<Unit> = handlerFor(url).handleStreamingEvent(span, event, index)

    /**
     * Determines the appropriate handler for an Anthropic API based on the given URL.
     *
     * @param endpoint The URL used to detect the API type and determine the corresponding handler.
     * @return An instance of [EndpointApiHandler] that is capable of handling requests for the detected API type.
     */
    private fun handlerFor(endpoint: TracyHttpUrl): EndpointApiHandler {
        val apiType = AnthropicApiType.detect(endpoint)
        return when (apiType) {
            AnthropicApiType.COUNT_TOKENS -> countTokensHandler
            AnthropicApiType.BATCHES -> batchesHandler
            AnthropicApiType.FILES -> filesHandler
            AnthropicApiType.MODELS -> modelsHandler
            AnthropicApiType.MESSAGES -> handlers.getOrPut(AnthropicApiType.MESSAGES) {
                MessagesAnthropicApiEndpointHandler()
            }
            null -> handlers.getOrPut(AnthropicApiType.MESSAGES) {
                logger.warn { "Unknown Anthropic API detected. Defaulting to 'messages'." }
                MessagesAnthropicApiEndpointHandler()
            }
        }
    }

    private val logger = KotlinLogging.logger {}
}
