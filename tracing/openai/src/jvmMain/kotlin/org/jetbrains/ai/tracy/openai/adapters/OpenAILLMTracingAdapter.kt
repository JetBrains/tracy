/*
 * Copyright © 2026 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.ai.tracy.openai.adapters

import org.jetbrains.ai.tracy.core.adapters.LLMTracingAdapter
import org.jetbrains.ai.tracy.core.adapters.handlers.EndpointApiHandler
import org.jetbrains.ai.tracy.core.adapters.media.MediaContentExtractorImpl
import org.jetbrains.ai.tracy.core.http.protocol.*
import org.jetbrains.ai.tracy.openai.adapters.handlers.ChatCompletionsOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.OpenAIApiUtils
import org.jetbrains.ai.tracy.openai.adapters.handlers.ResponsesOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesCreateEditOpenAIApiEndpointHandler
import org.jetbrains.ai.tracy.openai.adapters.handlers.images.ImagesCreateOpenAIApiEndpointHandler
import io.opentelemetry.api.trace.Span
import io.opentelemetry.semconv.incubating.GenAiIncubatingAttributes.GenAiSystemIncubatingValues
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap


/**
 * Detects which OpenAI API is being used based on the request / response structure
 */
private enum class OpenAIApiType(val route: String) {
    // See: https://platform.openai.com/docs/api-reference/completions
    CHAT_COMPLETIONS("completions"),

    // See: https://platform.openai.com/docs/api-reference/responses
    RESPONSES_API("responses"),

    // See: https://platform.openai.com/docs/api-reference/images/create
    IMAGES_GENERATIONS("images/generations"),

    // See: https://platform.openai.com/docs/api-reference/images/createEdit
    IMAGES_EDITS("images/edits");

    companion object {
        fun detect(url: TracyHttpUrl): OpenAIApiType? {
            val route = url.pathSegments.joinToString(separator = "/")
            return entries.firstOrNull { route.contains(it.route) }
        }
    }
}

/**
 * Tracing adapter for OpenAI API.
 *
 * Automatically detects and handles multiple OpenAI API endpoints including chat completions,
 * responses API, and image operations (generation, editing). Uses specialized handlers for each
 * endpoint type to extract telemetry data including model parameters, messages, tool calls,
 * streaming, and media content.
 *
 * ## Supported Endpoints
 * - **Chat Completions**: `/v1/chat/completions`
 * - **Responses API**: `/v1/responses`
 * - **Image Generation**: `/v1/images/generations`
 * - **Image Editing**: `/v1/images/edits`
 *
 * ## Example Usage
 * ```kotlin
 * val client = instrument(HttpClient(), OpenAILLMTracingAdapter())
 *
 * // Chat completions
 * client.post("https://api.openai.com/v1/chat/completions") {
 *     header("Authorization", "Bearer $apiKey")
 *     setBody("""
 *         {
 *             "messages": [{"role": "user", "content": "Hello!"}],
 *             "model": "gpt-4o-mini"
 *         }
 *     """)
 * }
 * // Automatically detects endpoint and traces accordingly
 * ```
 *
 * See: [OpenAI API Reference](https://platform.openai.com/docs/api-reference)
 */
class OpenAILLMTracingAdapter : LLMTracingAdapter(genAISystem = GenAiSystemIncubatingValues.OPENAI) {
    private val handlers = ConcurrentHashMap<OpenAIApiType, EndpointApiHandler>()

    override fun getRequestBodyAttributes(span: Span, request: TracyHttpRequest) {
        val handler = handlerFor(request.url)
        handler.handleRequestAttributes(span, request)
    }

    override fun getResponseBodyAttributes(span: Span, response: TracyHttpResponse) {
        val handler = handlerFor(response.url)
        OpenAIApiUtils.setCommonResponseAttributes(span, response)
        handler.handleResponseAttributes(span, response)
    }

    override fun getSpanName(request: TracyHttpRequest) = "OpenAI-generation"

    override fun isStreamingRequest(request: TracyHttpRequest): Boolean {
        return when (request.body) {
            is TracyHttpRequestBody.FormData -> {
                val data = request.body.asFormData() ?: return false
                data.parts.filter { it.name == "stream" }.any {
                    val value = it.content.toString(it.contentType?.charset() ?: Charsets.UTF_8)
                    value.toBooleanStrictOrNull() ?: false
                }
            }
            is TracyHttpRequestBody.Json -> {
                val body = request.body.asJson()?.jsonObject ?: return false
                body["stream"]?.jsonPrimitive?.boolean ?: false
            }
        }
    }

    override fun handleStreaming(span: Span, url: TracyHttpUrl, events: String) {
        val handler = handlerFor(url)
        handler.handleStreaming(span, events)
    }

    /**
     * Determines the appropriate handler for an OpenAI API based on the given URL.
     *
     * @param endpoint The URL used to detect the API type and determine the corresponding handler.
     * @return An instance of [EndpointApiHandler] that is capable of handling requests for the detected API type.
     */
    private fun handlerFor(endpoint: TracyHttpUrl): EndpointApiHandler {
        val apiType = OpenAIApiType.detect(endpoint)
        val extractor = MediaContentExtractorImpl()

        val handler = when (apiType) {
            OpenAIApiType.CHAT_COMPLETIONS -> handlers.getOrPut(OpenAIApiType.CHAT_COMPLETIONS) {
                ChatCompletionsOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.RESPONSES_API -> handlers.getOrPut(OpenAIApiType.RESPONSES_API) {
                ResponsesOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.IMAGES_GENERATIONS -> handlers.getOrPut(OpenAIApiType.IMAGES_GENERATIONS) {
                ImagesCreateOpenAIApiEndpointHandler(extractor)
            }

            OpenAIApiType.IMAGES_EDITS -> handlers.getOrPut(OpenAIApiType.IMAGES_EDITS) {
                ImagesCreateEditOpenAIApiEndpointHandler(extractor)
            }

            null -> handlers.getOrPut(OpenAIApiType.CHAT_COMPLETIONS) {
                logger.warn { "Unknown OpenAI API detected. Defaulting to 'chat completion'." }
                ChatCompletionsOpenAIApiEndpointHandler(extractor)
            }
        }
        return handler
    }

    private val logger = KotlinLogging.logger {}
}
